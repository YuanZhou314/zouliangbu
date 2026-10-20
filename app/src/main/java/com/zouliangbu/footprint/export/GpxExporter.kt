package com.zouliangbu.footprint.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.zouliangbu.footprint.data.db.TrackPoint
import com.zouliangbu.footprint.data.db.TrackSession
import java.io.File

/**
 * 把一条会话落成 GPX 文件并交给系统分享。
 *
 * 为什么不写进公共存储：Android 10+ 分区存储下 WRITE_EXTERNAL_STORAGE 已失效，
 * 走 MediaStore 又只能存媒体类型。GPX 是"临时产物"——写 cacheDir 再用 FileProvider
 * 授权给接收方，既不需要任何存储权限，也不会在用户手机里留垃圾。
 *
 * authority 约定：`${applicationId}.fileprovider`（见 AndroidManifest 的 provider 节点
 * 与 res/xml/file_paths.xml），两处必须一致，改了要一起改。
 */
object GpxExporter {

    private const val AUTHORITY_SUFFIX = ".fileprovider"

    /** 导出目录名，与 res/xml/file_paths.xml 的 `<cache-path path="gpx/">` 对应。 */
    private const val DIR = "gpx"

    /**
     * 写文件并返回 content:// Uri。
     * 同名文件直接覆盖（文件名精确到分钟，同一分钟内重复导出以最后一次为准）。
     */
    fun export(context: Context, session: TrackSession, points: List<TrackPoint>): Uri {
        val dir = File(context.cacheDir, DIR).apply { mkdirs() }
        val file = File(dir, GpxWriter.suggestFileName(session))
        file.outputStream().use { GpxWriter.write(session, points, it) }
        return FileProvider.getUriForFile(context, context.packageName + AUTHORITY_SUFFIX, file)
    }

    /** 系统分享面板。 */
    fun shareIntent(uri: Uri): Intent {
        val send = Intent(Intent.ACTION_SEND).apply {
            // 语义上应该是 application/gpx+xml；若某些国产 App（如微信）在分享面板里不出现，
            // 改成 "application/octet-stream" 兼容性更好，文件名仍是 .gpx，不影响接收方识别。
            type = "application/gpx+xml"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "GPS 轨迹（GPX）")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "导出 GPS 轨迹")
    }
}
