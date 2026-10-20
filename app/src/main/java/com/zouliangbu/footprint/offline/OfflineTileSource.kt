package com.zouliangbu.footprint.offline

import android.content.Context
import android.util.Log
import org.osmdroid.tileprovider.MapTileProviderArray
import org.osmdroid.tileprovider.MapTileProviderBase
import org.osmdroid.tileprovider.modules.IArchiveFile
import org.osmdroid.tileprovider.modules.MapTileDownloader
import org.osmdroid.tileprovider.modules.MapTileFileArchiveProvider
import org.osmdroid.tileprovider.modules.MapTileModuleProviderBase
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.util.SimpleRegisterReceiver
import java.io.File

/**
 * 离线瓦片接入（ROADMAP P4.1）。
 *
 * osmdroid 的正确用法不是「给 ITileSource 加 archive」，而是**换掉整个 tile provider**：
 *   - [MapTileFileArchiveProvider]：从本机 MBTiles 读离线瓦片；
 *   - [MapTileDownloader]：在线下载（极图 GeoQ）。
 * 两者组成 [MapTileProviderArray]，请求按顺序尝试：
 * 先查离线包（命中就用），没命中再走在线。
 *
 * 这正是我们要的语义：**有网用在线最新的，没网用已下载的**。
 *
 * 坐标系：MBTiles 里是 Web Mercator 栅格瓦片，坐标系由 ITileSource 决定。
 * 在线源是极图 GeoQ（GCJ-02），离线包也按 GCJ-02 切，两者才能对齐。
 */
object OfflineTileSource {

    private const val TAG = "OfflineTiles"

    /**
     * 构造「离线优先 + 在线兜底」的 tile provider。
     *
     * @param context   用于注册广播接收器
     * @param online    在线瓦片源（极图 GeoQ）
     * @param mbtilesFiles 本机已安装的 MBTiles 文件（可为空 = 纯在线）
     * @return 可直接塞给 `MapView(context, tileProvider)` 的 provider
     */
    fun createProvider(
        context: Context,
        online: ITileSource,
        mbtilesFiles: List<File>
    ): MapTileProviderBase {
        val registerReceiver = SimpleRegisterReceiver(context)

        val providers = ArrayList<MapTileModuleProviderBase>(2)

        // 1) 离线：每个已安装的 MBTiles 一个 archive provider。
        for (file in mbtilesFiles) {
            val archive = openArchive(file)
            if (archive != null) {
                providers.add(
                    MapTileFileArchiveProvider(registerReceiver, online, arrayOf<IArchiveFile>(archive))
                )
            }
        }

        // 2) 在线兜底。
        providers.add(MapTileDownloader(online))

        return MapTileProviderArray(online, registerReceiver, providers.toTypedArray())
    }

    /**
     * 打开一个 MBTiles 文件。
     *
     * osmdroid 的 `MBTilesFileArchive` 构造器是 private，官方入口是 [MBTilesFileArchive.getDatabaseFileArchive]；
     * 它要求文件是合法的 SQLite 库，损坏的包会抛 SQLiteException——这里吞掉并跳过该包，
     * 不能因为一个坏包让整个地图打不开。
     */
    fun openArchive(file: File): IArchiveFile? {
        return try {
            if (!file.isFile || file.length() <= 0L) return null
            org.osmdroid.tileprovider.modules.MBTilesFileArchive.getDatabaseFileArchive(file)
        } catch (t: Throwable) {
            Log.w(TAG, "open mbtiles failed: $file", t)
            null
        }
    }

    /**
     * 找到本机所有已安装的离线包。
     *
     * @param manager 离线包管理器
     * @param regionIds 已安装的区域 id 列表（为空表示不过滤）
     */
    fun installedFiles(
        manager: OfflinePackageManager,
        regionIds: Collection<String>
    ): List<File> {
        if (regionIds.isEmpty()) return emptyList()
        return regionIds.mapNotNull { manager.packageFile(it) }
    }
}
