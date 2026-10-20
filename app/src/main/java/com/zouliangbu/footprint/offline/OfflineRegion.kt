package com.zouliangbu.footprint.offline

/**
 * 离线地图包的元信息（ROADMAP P4.1）。
 *
 * 一个「区域包」= 一份 MBTiles 文件 + 它的清单条目。
 * 清单描述了区域边界、包大小、校验和、版本——离线包要能被校验、增量更新、删除，
 * 这些信息必须跟着包一起存，不能只记一个文件名。
 *
 * 坐标系：MBTiles 内部存的是 **Web Mercator 瓦片**，本身不带坐标系信息，
 * 渲染时按瓦片源的坐标系解释。本项目离线包统一用 **GCJ-02**（与极图 GeoQ 在线瓦片一致），
 * 与 App 端全链路 GCJ-02 的约束吻合。
 */
data class OfflineRegion(
    /** 区域唯一 id（清单 JSON 里的 key）。 */
    val id: String,
    /** 区域名，如「浙江省」。 */
    val name: String,
    /** 所属大区，如「华东」。 */
    val group: String,
    /** 离线包下载地址（MBTiles 文件）。 */
    val downloadUrl: String,
    /** 包文件大小（字节）。 */
    val sizeBytes: Long,
    /** SHA-256 校验和（小写十六进制）。下载后必须校验，不符就删掉重下。 */
    val sha256: String,
    /** 清单版本号：同一个 region 的新版本会覆盖旧版本。 */
    val version: String,
    /** 覆盖范围（GCJ-02 经纬度）。 */
    val bounds: GeoBounds,
    /** 最低/最高缩放级别。 */
    val minZoom: Int,
    val maxZoom: Int
) {
    /** 人类可读的体积，如「128 MB」。 */
    val sizeText: String
        get() {
            val mb = sizeBytes / 1024.0 / 1024.0
            return if (mb >= 1024.0) "%.1f GB".format(mb / 1024.0)
            else if (mb >= 1.0) "%.0f MB".format(mb)
            else "%.0f KB".format(sizeBytes / 1024.0)
        }
}

/** 经纬度范围。 */
data class GeoBounds(
    val minLat: Double,
    val minLng: Double,
    val maxLat: Double,
    val maxLng: Double
) {
    /** 该范围是否包含给定点。 */
    fun contains(lat: Double, lng: Double): Boolean =
        lat in minLat..maxLat && lng in minLng..maxLng

    companion object {
        /** 空范围（不包含任何点）。 */
        val EMPTY = GeoBounds(0.0, 0.0, 0.0, 0.0)
    }
}

/**
 * 离线包在本机的安装状态。
 *
 * [status] 的流转：
 *   NOT_DOWNLOADED → DOWNLOADING → INSTALLED
 *   DOWNLOADING → FAILED（可重试）
 *   INSTALLED → DOWNLOADING（同区域新版本，下载完覆盖）
 */
data class OfflinePackageState(
    val region: OfflineRegion,
    val status: Status,
    /** 已下载字节数（下载中才有意义）。 */
    val downloadedBytes: Long = 0L,
    /** 失败原因。 */
    val errorMessage: String? = null,
    /** 本机已安装的版本（仅 INSTALLED 时有意义）。 */
    val installedVersion: String? = null
) {
    enum class Status {
        NOT_DOWNLOADED,
        DOWNLOADING,
        INSTALLED,
        FAILED
    }

    val isInstalled: Boolean get() = status == Status.INSTALLED

    /** 下载进度 0..1。 */
    val progress: Float
        get() {
            if (status != Status.DOWNLOADING) return 0f
            if (region.sizeBytes <= 0L) return 0f
            return (downloadedBytes.toDouble() / region.sizeBytes).coerceIn(0.0, 1.0).toFloat()
        }
}
