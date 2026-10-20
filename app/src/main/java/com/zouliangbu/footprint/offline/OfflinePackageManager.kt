package com.zouliangbu.footprint.offline

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * 离线地图包管理器（ROADMAP P4.1 / P4.3）。
 *
 * 职责：
 *   - 下载 MBTiles 包到 app 私有目录（`getExternalFilesDir` 优先，卸载自动清理）；
 *   - 下载完成后**校验 SHA-256**，不符直接删掉——半个包在离线时只会显示空白瓦片，
 *     比没有包更糟（用户以为地图坏了）；
 *   - 记录每个区域的安装状态与版本，供 UI 展示；
 *   - 总量上限与清理（ROADMAP P4.3）。
 *
 * 存储位置：`getExternalFilesDir("offline_maps")`。
 * 不用 `getExternalStorageDirectory`：Android 10+ 分区存储下拿不到写权限，
 * 而且外部目录卸载后可能残留。app 专属外部目录免权限、卸载即清。
 *
 * 引擎耦合（重要）：离线瓦片**只有 osmdroid 能读**（`MBTilesFileArchive`），
 * 高德 MapView 不支持 MBTiles。所以离线包只对 osmdroid 分支有效；
 * 有高德 Key 时下载的包用不上——这是 ROADMAP P4.4 要评估的底座问题。
 */
class OfflinePackageManager(private val context: Context) {

    companion object {
        private const val TAG = "OfflinePkg"

        /** 存储目录名（放在 app 专属外部目录下）。 */
        private const val DIR_NAME = "offline_maps"

        /** 单个包的体积上限（字节）。超过就不给下——防止用户下到手机爆空间。 */
        const val MAX_PACKAGE_BYTES = 512L * 1024L * 1024L

        /** 全部离线包的总量上限（字节）。默认 2 GB。 */
        const val MAX_TOTAL_BYTES = 2L * 1024L * 1024L * 1024L

        /** 下载超时（毫秒）。 */
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 30_000

        /** 状态文件名。 */
        private const val STATE_FILE = "packages.json"
    }

    private val _states = MutableStateFlow<Map<String, OfflinePackageState>>(emptyMap())
    /** 各区域的安装状态，key = region id。 */
    val states: StateFlow<Map<String, OfflinePackageState>> = _states.asStateFlow()

    /** 离线包根目录。取不到外部目录就退回内部存储（app 私有目录，同样免权限）。 */
    fun rootDir(): File {
        val external = context.getExternalFilesDir(DIR_NAME)
        val dir = external ?: File(context.filesDir, DIR_NAME)
        dir.mkdirs()
        return dir
    }

    /** 某个区域已安装的包文件（未安装返回 null）。 */
    fun packageFile(regionId: String): File? {
        val f = File(rootDir(), "$regionId.mbtiles")
        return if (f.isFile && f.length() > 0) f else null
    }

    /** 已安装包的总体积（字节）。 */
    fun totalUsedBytes(): Long =
        rootDir().listFiles { f -> f.extension == "mbtiles" }
            ?.sumOf { it.length() } ?: 0L

    /**
     * 刷新状态：扫描目录，把已存在的包标为 INSTALLED，其余标为 NOT_DOWNLOADED。
     *
     * 启动时调一次：App 可能被杀、包可能被别人删，状态要以**磁盘实际内容**为准，
     * 不能只信内存里缓存的 JSON。
     */
    fun refresh(regions: List<OfflineRegion>) {
        val map = HashMap<String, OfflinePackageState>()
        for (r in regions) {
            val file = packageFile(r.id)
            map[r.id] = if (file != null) {
                OfflinePackageState(
                    region = r,
                    status = OfflinePackageState.Status.INSTALLED,
                    installedVersion = readInstalledVersion(r.id)
                )
            } else {
                OfflinePackageState(r, OfflinePackageState.Status.NOT_DOWNLOADED)
            }
        }
        _states.value = map
    }

    /**
     * 下载并安装一个离线包。
     *
     * @param onProgress 进度回调（已下载字节 / 总字节）
     * @return 成功返回 true；失败返回 false（状态里带原因）
     */
    suspend fun download(
        region: OfflineRegion,
        onProgress: (Long, Long) -> Unit = { _, _ -> }
    ): Boolean = withContext(Dispatchers.IO) {
        mark(region.id, OfflinePackageState.Status.DOWNLOADING)
        try {
            if (region.sizeBytes > MAX_PACKAGE_BYTES) {
                fail(region.id, "包体积 ${region.sizeText} 超过上限 512 MB")
                return@withContext false
            }
            if (totalUsedBytes() + region.sizeBytes > MAX_TOTAL_BYTES) {
                fail(region.id, "离线包总量将超过 2 GB，请先删除一些区域")
                return@withContext false
            }

            val target = File(rootDir(), "$region.id.mbtiles.part")
            val final = File(rootDir(), "$region.id.mbtiles")
            // 先删残留的 .part：上次下载中断留下的半包。
            if (target.exists()) target.delete()

            val total = downloadTo(region.downloadUrl, target, region.sizeBytes, onProgress)
            if (total <= 0L) {
                target.delete()
                fail(region.id, "下载失败")
                return@withContext false
            }

            // 校验：不符就删掉。半个包在离线时只显示空白，比没有更糟。
            if (region.sha256.isNotBlank()) {
                val actual = sha256(target)
                if (!actual.equals(region.sha256, ignoreCase = true)) {
                    target.delete()
                    fail(region.id, "校验失败（sha256 不匹配），已删除重下")
                    return@withContext false
                }
            }

            // 用 rename 而不是 copy：同一分区下是原子操作，不会出现「目标存在但只有一半」。
            if (final.exists()) final.delete()
            if (!target.renameTo(final)) {
                fail(region.id, "安装失败（重命名失败）")
                return@withContext false
            }
            writeInstalledVersion(region.id, region.version)
            markInstalled(region.id, region)
            true
        } catch (t: Throwable) {
            Log.e(TAG, "download region=${region.id} failed", t)
            fail(region.id, t.message ?: "下载失败")
            false
        }
    }

    /** 删除一个已安装的离线包。 */
    suspend fun delete(regionId: String): Boolean = withContext(Dispatchers.IO) {
        val file = packageFile(regionId) ?: return@withContext true
        val ok = file.delete()
        if (ok) {
            writeInstalledVersion(regionId, null)
            _states.value = _states.value.toMutableMap().apply {
                remove(regionId)
            }
        }
        ok
    }

    /** 删除全部离线包。 */
    suspend fun deleteAll(): Boolean = withContext(Dispatchers.IO) {
        val ok = rootDir().listFiles { f -> f.extension == "mbtiles" }
            ?.all { it.delete() } ?: true
        _states.value = emptyMap()
        ok
    }

    // ===== 内部 =====

    /** 下载到文件，返回总字节数（失败返回 -1）。 */
    private fun downloadTo(
        url: String,
        target: File,
        expectedBytes: Long,
        onProgress: (Long, Long) -> Unit
    ): Long {
        var conn: HttpURLConnection? = null
        var input: InputStream? = null
        var output = target.outputStream()
        try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                requestMethod = "GET"
            }
            if (conn.responseCode !in 200..299) {
                Log.w(TAG, "http ${conn.responseCode} for $url")
                return -1L
            }
            val total = if (conn.contentLengthLong > 0) conn.contentLengthLong else expectedBytes
            input = conn.inputStream
            val buffer = ByteArray(64 * 1024)
            var written = 0L
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                output.write(buffer, 0, read)
                written += read
                onProgress(written, total)
            }
            output.flush()
            return written
        } finally {
            runCatching { output.close() }
            runCatching { input?.close() }
            runCatching { conn?.disconnect() }
        }
    }

    /** 计算文件 SHA-256（小写十六进制）。 */
    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun mark(id: String, status: OfflinePackageState.Status) {
        _states.value = _states.value.toMutableMap().apply {
            val old = this[id]
            val region = old?.region ?: return@apply
            this[id] = OfflinePackageState(region, status)
        }
    }

    private fun fail(id: String, message: String) {
        _states.value = _states.value.toMutableMap().apply {
            val old = this[id] ?: return@apply
            this[id] = old.copy(status = OfflinePackageState.Status.FAILED, errorMessage = message)
        }
    }

    private fun markInstalled(id: String, region: OfflineRegion) {
        _states.value = _states.value.toMutableMap().apply {
            this[id] = OfflinePackageState(
                region = region,
                status = OfflinePackageState.Status.INSTALLED,
                installedVersion = region.version
            )
        }
    }

    /** 版本号的持久化：放在 state 文件旁边的 `.version` 小文件里，一个区域一个。 */
    private fun writeInstalledVersion(regionId: String, version: String?) {
        val f = File(rootDir(), "$regionId.version")
        if (version == null) {
            f.delete()
        } else {
            f.writeText(version)
        }
    }

    private fun readInstalledVersion(regionId: String): String? {
        val f = File(rootDir(), "$regionId.version")
        return if (f.isFile) f.readText().trim().takeIf { it.isNotEmpty() } else null
    }
}
