package com.zouliangbu.footprint.ui.tracks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.zouliangbu.footprint.plants.PlantSpecies

/**
 * 「沿途常见植物」横滑卡片区。
 *
 * 数据来自本地 assets（行政区 -> TopN 物种，预计算产物，离线可用）；
 * 图片运行时从 iNaturalist 拉取（仅 CC 开放许可），Coil 内存/磁盘缓存，不单独落盘。
 * 未命中行政区的轨迹整块不显示，所以这里不做任何空态。
 */
@Composable
fun PlantGuideSection(
    regionName: String,
    species: List<PlantSpecies>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            "沿途常见植物",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            "基于 $regionName 一带的开源观测数据（GBIF / iNaturalist）",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
        )
        Spacer(Modifier.height(10.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 2.dp)
        ) {
            items(species, key = { it.scientificName }) { sp ->
                PlantCard(sp)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "图片来自 iNaturalist 观察者（CC 许可，逐张署名见分享页）",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun PlantCard(species: PlantSpecies) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f),
        modifier = Modifier.width(132.dp)
    ) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(92.dp)
                    .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                val url = species.photo?.url
                if (url != null) {
                    AsyncImage(
                        model = url,
                        contentDescription = species.nameCn ?: species.scientificName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // 无开放许可图片：灰底放物种名首字，不放大文字块
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            (species.nameCn ?: species.scientificName).take(1),
                            fontSize = 28.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }
            Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                Text(
                    species.nameCn ?: species.scientificName,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                // 有中文名时第二行放学名（斜体是学名排印惯例）；没有时给一句来源说明
                Text(
                    if (species.nameCn != null) species.scientificName else "学名待校对",
                    fontSize = 10.sp,
                    fontStyle = if (species.nameCn != null) FontStyle.Italic else FontStyle.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}
