package com.zouliangbu.footprint.share

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.zouliangbu.footprint.util.TimeFmt
import java.io.File

/**
 * 分享（ROADMAP P5.3 位置分享 / P5.4 行程卡分享图）。
 *
 * 两种分享：
 *   - P5.3 **位置分享**：把当前 GCJ-02 坐标 + 一句话文本，用系统分享面板发出去。
 *   - P5.4 **行程卡分享图**：把一次轨迹的统计数据 + 一句话，拼成一张图分享。
 *     图片由 ShareCardRenderer 渲染（纯 Android Canvas）。
 *
 * 设计要点：
 *   1. **GCJ-02 坐标原样分享**——不做转换（用户在国内用高德生态）。
 *   2. **通过 FileProvider 共享图片**——直接 file:// URI 在 Android 7+ 会崩。
 *   3. **不引入额外序列化依赖**——纯字符串拼装。
 */
object Share {

    /** 分享一个位置。 */
    fun shareLocation(
        context: Context,
        lat: Double,
        lng: Double,
        title: String,
        extraNote: String? = null
    ) {
        val text = buildString {
            append(title)
            append("\n")
            append("坐标：")
            append("%.6f, %.6f".format(lat, lng))
            append("（GCJ-02）")
            if (!extraNote.isNullOrBlank()) {
                append("\n")
                append(extraNote)
            }
            append("\n")
            append("— 来自 走两步")
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_SUBJECT, title)
        }
        context.startActivity(Intent.createChooser(intent, "分享位置"))
    }

    /** 分享一个图片文件（FileProvider URI）。 */
    fun shareImage(context: Context, imageFile: File, title: String) {
        val uri = FileProvider.getUriForFile(
            context,
            context.packageName + ".fileprovider",
            imageFile
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "分享行程卡"))
    }

    /** 拼一句适合分享的行程摘要。 */
    fun trackSummary(
        name: String?,
        startTimeMs: Long,
        distanceMeters: Double,
        activeDurationMs: Long,
        ascentMeters: Double
    ): String {
        val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            .format(java.util.Date(startTimeMs))
        return buildString {
            append("走两步")
            if (!name.isNullOrBlank()) {
                append(" · ")
                append(name)
            }
            append(" · ")
            append(date)
            append(" · ")
            append(TimeFmt.distance(distanceMeters.toFloat()))
            append(" · ")
            append(TimeFmt.elapsed(activeDurationMs))
            if (ascentMeters >= 1.0) {
                append(" · 爬升 ")
                append("%.0f m".format(ascentMeters))
            }
        }
    }
}
