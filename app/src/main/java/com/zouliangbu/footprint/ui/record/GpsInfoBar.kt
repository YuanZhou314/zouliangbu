package com.zouliangbu.footprint.ui.record

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 顶部 GPS 仪表条（仿两步路记录页顶部）。
 *
 * 展示 GCJ-02 坐标基准、纬度/经度（6 位小数）、海拔、水平误差。
 * 数据来自共享 [com.zouliangbu.footprint.location.LocationProvider]，
 * 与地图蓝点同源，不需要另起定位客户端。
 *
 * 设计取舍（2026-10-21 评估）：
 *   - **卫星数不显示**：AMapLocation 虽有 getSatellites()，但高德只有 GPS 类型定位才回填该值，
 *     网络定位时常为 0，显示「卫星 0」比不显示更误导；且真卫星数据要接 GnssStatus，
 *     与「全 App 单一 AMapLocationClient」架构冲突。
 *   - **误差 >30 m 染橙**：与 TrackMetrics 的 30 m 精度过滤口径一致，提醒用户此刻定位质量差、
 *     轨迹可能不准。
 *
 * @param lat       纬度（GCJ-02），null 表示尚未定位。
 * @param lng       经度（GCJ-02），null 表示尚未定位。
 * @param altitude  海拔（米），null 或缺失显示「--」。
 * @param accuracy  水平误差（米），null 显示「--」。
 */
@Composable
fun GpsInfoBar(
    lat: Double?,
    lng: Double?,
    altitude: Double?,
    accuracy: Float?,
    modifier: Modifier = Modifier
) {
    val accColor = when {
        accuracy == null -> Color.White
        accuracy > 30f -> Color(0xFFFFB300) // 橙：精度差，与 TrackMetrics 30m 阈值呼应
        else -> Color.White
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xB3263238))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 坐标基准标签：高德输出是 GCJ-02，不是 WGS-84（两步路标 WGS84 是它自己内部转过）。
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFFE65100))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = "GCJ02",
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // 坐标 + 海拔 + 误差，两行紧凑排版。
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (lat != null && lng != null) {
                    "%.6f, %.6f".format(lat, lng)
                } else {
                    "等待定位…"
                },
                color = Color.White,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1
            )
            Spacer(Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "海拔 ${altitude?.let { "%.0f m".format(it) } ?: "--"}",
                    color = Color(0xB3FFFFFF),
                    fontSize = 11.sp
                )
                Text(
                    text = "±${accuracy?.let { "%.0f m".format(it) } ?: "--"}",
                    color = accColor,
                    fontSize = 11.sp
                )
            }
        }
    }
}
