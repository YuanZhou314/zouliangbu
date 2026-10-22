package com.zouliangbu.footprint.ui.tracks

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zouliangbu.footprint.util.TrackSeries

/**
 * Y 轴刻度槽的宽度。
 *
 * 这个值按"最长的刻度文字"定，不是按感觉给：44dp 时数字只占 43px、槽却占 132px，
 * 左侧就凭空多出一条空白（用户第一眼就发现了）。收到 30dp（90px）刚好放得下
 * 6 个字符（配速的 "98:10"），轴线也跟着左移约 50px，绘图区更宽。
 */
private val AXIS_W = 30.dp

/** 曲线区高度（不含轴标签）。 */
private val PLOT_H = 112.dp

/**
 * 轨迹曲线图（速度 / 配速 / 海拔 / 累计爬升下降）。
 *
 * 结构照着主流户外 App 的样张做：标题独占一行，数值摘要独占一行，
 * 然后是「左侧 Y 轴刻度 + 网格 + 曲线」，最下面是 X 轴公里刻度。
 *
 * 刻度用 Compose 布局画而不是 Canvas.drawText：字体跟主题一致，也不用管 sp 到 px 的换算。
 * Canvas 只负责网格、面积、折线和平均线。
 *
 * 纵轴用 TrackSeries.Series 的 1%/99% 分位值，单个 GPS 尖峰不会把整张图压扁；
 * 越界值 clamp 到边缘，宁可贴边也不让曲线出框。
 */
@Composable
fun TrackChart(
    series: TrackSeries.Series,
    title: String,
    unit: String,
    color: Color,
    /** 横轴总长（公里），用来算底部刻度文字。 */
    xTotal: Float,
    modifier: Modifier = Modifier,
    /** 平均线（虚线）：让这一段比平均快还是慢一眼可见。 */
    average: Float? = null,
    /** 第二条曲线（如累计下降）。两条共用纵轴，否则同图比较没意义。 */
    second: TrackSeries.Series? = null,
    secondColor: Color? = null,
    secondLabel: String? = null,
    /** 数值摘要，独占一行显示在标题下方。 */
    summary: String? = null,
    format: (Float) -> String = { "%.0f".format(it) }
) {
    val secondDrawable = second != null && second.drawable

    Column(modifier.fillMaxWidth()) {
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium)

        if (summary != null) {
            Spacer(Modifier.height(3.dp))
            Text(
                summary,
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
        }

        if (secondDrawable) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LegendDot(color)
                Spacer(Modifier.width(4.dp))
                Text("爬升", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(12.dp))
                LegendDot(secondColor ?: color)
                Spacer(Modifier.width(4.dp))
                Text(
                    secondLabel ?: "下降",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        if (!series.drawable) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(PLOT_H)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "数据不足，画不出曲线",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Column
        }

        val lo0 = series.min
        val hi0 = series.max
        val lo = if (secondDrawable) minOf(lo0, second!!.min) else lo0
        val hi = if (secondDrawable) maxOf(hi0, second!!.max) else hi0
        val mid = (lo + hi) / 2f

        Row(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.width(AXIS_W).height(PLOT_H),
                verticalArrangement = Arrangement.SpaceBetween,
                // 左对齐：数字与上面的标题左侧对齐，左侧不再有一条空槽。
                // 右对齐时数字贴轴线，多出来的宽度全落在屏幕最左边，看起来像凭空留白。
                horizontalAlignment = Alignment.Start
            ) {
                AxisLabel(format(hi))
                AxisLabel(format(mid))
                AxisLabel(format(lo))
            }
            Spacer(Modifier.width(6.dp))

            val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.16f)
            val axisColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
            val areaTop = color.copy(alpha = 0.30f)
            val areaBottom = color.copy(alpha = 0.02f)
            val avgColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
            val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)

            Canvas(Modifier.weight(1f).height(PLOT_H)) {
                val w = size.width
                val h = size.height
                val span = (hi - lo).takeIf { it > 0f } ?: 1f

                fun yOf(v: Float): Float {
                    val t = ((v - lo) / span).coerceIn(0f, 1f)
                    return h - t * h
                }

                for (i in 0..4) {
                    val x = w * i / 4f
                    drawLine(gridColor, Offset(x, 0f), Offset(x, h), strokeWidth = 1f)
                }
                drawLine(axisColor, Offset(0f, 0f), Offset(0f, h), strokeWidth = 1.4f)

                for (i in 0..2) {
                    val y = h * i / 2f
                    drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
                }

                fun linePath(values: List<Float>): Path {
                    val nv = values.size
                    val step = if (nv > 1) w / (nv - 1) else w
                    return Path().apply {
                        moveTo(0f, yOf(values[0]))
                        for (i in 1 until nv) lineTo(i * step, yOf(values[i]))
                    }
                }

                val n = series.values.size
                val stepX = if (n > 1) w / (n - 1) else w
                val area = Path().apply {
                    moveTo(0f, h)
                    series.values.forEachIndexed { i, v -> lineTo(i * stepX, yOf(v)) }
                    lineTo((n - 1) * stepX, h)
                    close()
                }
                drawPath(area, Brush.verticalGradient(listOf(areaTop, areaBottom)))

                average?.let {
                    drawLine(
                        avgColor,
                        Offset(0f, yOf(it)),
                        Offset(w, yOf(it)),
                        strokeWidth = 1.2.dp.toPx(),
                        pathEffect = dash
                    )
                }

                if (secondDrawable) {
                    drawPath(
                        linePath(second!!.values),
                        secondColor ?: color,
                        style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                drawPath(
                    linePath(series.values),
                    color,
                    style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
                )
                drawCircle(
                    color,
                    radius = 3.dp.toPx(),
                    center = Offset((n - 1) * stepX, yOf(series.values.last()))
                )
            }
        }

        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(AXIS_W + 6.dp))
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceBetween) {
                val marks = listOf(0f, 0.25f, 0.5f, 0.75f, 1f)
                marks.forEachIndexed { i, f ->
                    val v = xTotal * f
                    XAxisLabel(
                        if (i == marks.lastIndex) "%.1f km".format(v) else "%.1f".format(v),
                        if (i == 0) TextAlign.Start
                        else if (i == marks.lastIndex) TextAlign.End
                        else TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun AxisLabel(text: String) {
    Text(
        text,
        fontSize = 9.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Start,
        maxLines = 1
    )
}

@Composable
private fun XAxisLabel(text: String, align: TextAlign) {
    Text(
        text,
        fontSize = 9.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = align,
        maxLines = 1
    )
}

@Composable
private fun LegendDot(color: Color) {
    Box(
        Modifier
            .size(7.dp)
            .clip(CircleShape)
            .background(color)
    )
}