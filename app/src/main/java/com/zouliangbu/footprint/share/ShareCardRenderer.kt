package com.zouliangbu.footprint.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.zouliangbu.footprint.ui.map.MapPoint
import com.zouliangbu.footprint.util.TimeFmt
import java.io.File
import java.io.FileOutputStream

/**
 * 行程卡分享图（ROADMAP P5.4）。
 *
 * 用纯 Canvas 渲染一张 **640 x 960** 的 PNG：
 *   - 上半：轨迹缩略图（按 bbox 适配 + 等比例绘制，描边加粗）
 *   - 中间：标题 + 距离 / 时长 / 爬升 三个数据格
 *   - 下半：日期 + 一句话摘要 + 底栏 logo 文字
 *
 * 设计要点：
 *   1. **固定尺寸**：避免在不同设备上生成不同分辨率的图，分享出去对比度不可控。
 *   2. **轨迹点已抽稀**（Polyline.simplify）——上万点直接画会卡住 Canvas。
 *   3. **背景色固定**：森林绿 (#1B5E20) + 暖白 (#F5F2E8)，与 App 主题色一致。
 *   4. **不使用 Compose**：分享图是 Bitmap 绘制，与 UI 层无关，方便复用与测试。
 */
object ShareCardRenderer {

    /** 固定画布尺寸。 */
    private const val WIDTH = 640
    private const val HEIGHT = 960

    /** 配色：森林绿 / 暖白 / 强调橙。 */
    private const val BG_COLOR = 0xFF1B5E20.toInt()
    private const val BG_LIGHT = 0xFFF5F2E8.toInt()
    private const val ACCENT = 0xFFE57C00.toInt()
    private const val TEXT_PRIMARY = 0xFF1B1B1B.toInt()
    private const val TEXT_SECONDARY = 0xFF6B6B6B.toInt()

    /**
     * 渲染一张行程卡 PNG。
     *
     * @param context  Context
     * @param name     轨迹名（可空，空时显示日期）
     * @param startTimeMs 开始时间（毫秒）
     * @param distanceMeters 距离（米）
     * @param activeDurationMs 有效时长（毫秒）
     * @param ascentMeters 累计爬升（米）
     * @param trackPoints 轨迹点（建议传入抽稀后的）
     * @return PNG 文件
     */
    fun render(
        context: Context,
        name: String?,
        startTimeMs: Long,
        distanceMeters: Double,
        activeDurationMs: Long,
        ascentMeters: Double,
        trackPoints: List<MapPoint>
    ): File {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        drawBackground(canvas)
        drawTrack(canvas, trackPoints)
        drawStats(canvas, name, startTimeMs, distanceMeters, activeDurationMs, ascentMeters)
        drawFooter(canvas, startTimeMs)

        val out = File(context.cacheDir, "share_card_" + startTimeMs + ".png")
        FileOutputStream(out).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
        }
        bitmap.recycle()
        return out
    }

    /** 画底色（上绿下白）。 */
    private fun drawBackground(canvas: Canvas) {
        canvas.drawColor(BG_COLOR)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = BG_LIGHT
            style = Paint.Style.FILL
        }
        // 下半张白卡：占高度 40%
        canvas.drawRect(RectF(0f, HEIGHT * 0.60f, WIDTH.toFloat(), HEIGHT.toFloat()), paint)
    }

    /** 画轨迹缩略图（按 bbox 适配 + 等比）。 */
    private fun drawTrack(canvas: Canvas, points: List<MapPoint>) {
        if (points.isEmpty()) return

        // 上半区（白色卡上方），留 60px padding。
        val trackArea = RectF(60f, 60f, (WIDTH - 60).toFloat(), HEIGHT * 0.55f)

        var minLat = Double.MAX_VALUE
        var maxLat = -Double.MAX_VALUE
        var minLng = Double.MAX_VALUE
        var maxLng = -Double.MAX_VALUE
        for (p in points) {
            if (p.lat < minLat) minLat = p.lat
            if (p.lat > maxLat) maxLat = p.lat
            if (p.lng < minLng) minLng = p.lng
            if (p.lng > maxLng) maxLng = p.lng
        }
        val latSpan = (maxLat - minLat).coerceAtLeast(0.0001)
        val lngSpan = (maxLng - minLng).coerceAtLeast(0.0001)

        // 等比例适配（x/y 缩放取小的那个）。
        val scaleX = trackArea.width() / lngSpan
        val scaleY = trackArea.height() / latSpan
        val scale = minOf(scaleX, scaleY)

        // 居中对齐。
        val offsetX = (trackArea.left + (trackArea.width() - lngSpan * scale) / 2).toFloat()
        val offsetY = (trackArea.top + (trackArea.height() - latSpan * scale) / 2).toFloat()

        val path = Path()
        for ((i, p) in points.withIndex()) {
            val x = offsetX + ((p.lng - minLng) * scale).toFloat()
            val y = offsetY + ((maxLat - p.lat) * scale).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        // 背景光晕（粗一点的浅色）。
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x33FFFFFF
            style = Paint.Style.STROKE
            strokeWidth = 12f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        canvas.drawPath(path, glow)

        // 主线条。
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 6f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        canvas.drawPath(path, line)

        // 起终点圆点。
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        if (points.isNotEmpty()) {
            val first = points.first()
            val last = points.last()
            dotPaint.color = ACCENT
            canvas.drawCircle(
                offsetX + ((first.lng - minLng) * scale).toFloat(),
                offsetY + ((maxLat - first.lat) * scale).toFloat(),
                12f,
                dotPaint
            )
            dotPaint.color = 0xFF2196F3.toInt()
            canvas.drawCircle(
                offsetX + ((last.lng - minLng) * scale).toFloat(),
                offsetY + ((maxLat - last.lat) * scale).toFloat(),
                12f,
                dotPaint
            )
        }
    }

    /** 画数据格。 */
    private fun drawStats(
        canvas: Canvas,
        name: String?,
        startTimeMs: Long,
        distanceMeters: Double,
        activeDurationMs: Long,
        ascentMeters: Double
    ) {
        val dateStr = java.text.SimpleDateFormat("MM月dd日 EEE", java.util.Locale.getDefault())
            .format(java.util.Date(startTimeMs))

        // 标题（白色大字）。
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 48f
            isFakeBoldText = true
        }
        val title = if (!name.isNullOrBlank()) name else "户外轨迹"
        canvas.drawText(title, 60f, HEIGHT * 0.66f, titlePaint)

        // 日期。
        val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x99FFFFFF.toInt()
            textSize = 24f
        }
        canvas.drawText(dateStr, 60f, (HEIGHT * 0.66f + 36f).toFloat(), datePaint)

        // 三格数据：距离 / 时长 / 爬升。
        val cellTop = HEIGHT * 0.72f
        val cellH = 120f
        val cellW = (WIDTH - 120).toFloat() / 3
        val values = listOf(
            TimeFmt.distance(distanceMeters.toFloat()) to "距离",
            TimeFmt.elapsed(activeDurationMs) to "时长",
            (if (ascentMeters >= 1.0) "%.0f m".format(ascentMeters) else "—") to "爬升"
        )
        for ((i, v) in values.withIndex()) {
            val left = 60f + cellW * i
            val right = left + cellW
            // 数值。
            val vPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = TEXT_PRIMARY
                textSize = 42f
                isFakeBoldText = true
            }
            canvas.drawText(v.first, left + 8f, cellTop + 56f, vPaint)
            // 标签。
            val lPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = TEXT_SECONDARY
                textSize = 22f
            }
            canvas.drawText(v.second, left + 8f, cellTop + 96f, lPaint)
            // 竖分割线。
            if (i < values.size - 1) {
                val sepPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0x22000000
                    strokeWidth = 1f
                }
                canvas.drawLine(right, cellTop, right, cellTop + cellH, sepPaint)
            }
        }
    }

    /** 画底栏（一句话摘要 + 品牌）。 */
    private fun drawFooter(canvas: Canvas, startTimeMs: Long) {
        val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            .format(java.util.Date(startTimeMs))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEXT_SECONDARY
            textSize = 22f
        }
        canvas.drawText("走两步 · " + date, 60f, HEIGHT - 60f, paint)

        // 右下角小图标文字。
        val brand = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ACCENT
            textSize = 22f
            isFakeBoldText = true
        }
        val text = "走两步"
        val width = brand.measureText(text)
        canvas.drawText(text, WIDTH - 60f - width, HEIGHT - 60f, brand)
    }
}
