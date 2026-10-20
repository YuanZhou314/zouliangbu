package com.zouliangbu.footprint.ui.tracks

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zouliangbu.footprint.FootprintApp
import com.zouliangbu.footprint.data.db.TrackSummary
import com.zouliangbu.footprint.export.GpxImport
import com.zouliangbu.footprint.export.GpxParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 导入结果，UI 侧据此 toast / 跳详情。 */
sealed interface ImportOutcome {
    data class Success(val sessionId: Long, val name: String?, val pointCount: Int) : ImportOutcome

    /** 失败一定是"可展示给用户的原因"（解析错 / 空文件 / 读不到），不要塞堆栈。 */
    data class Failure(val reason: String) : ImportOutcome
}

/**
 * 历史轨迹列表 ViewModel。
 *
 * 列表数据直接来自 Room 的 [TrackSummary] Flow（带点数），不在 ViewModel 里做二次聚合——
 * Room 的 Flow 已经是"表变了才发"的，再 map 一层只会多一次全量比较。
 */
class TrackListViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as FootprintApp).repository

    val tracks: StateFlow<List<TrackSummary>> = repo.observeCompleted()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing.asStateFlow()

    /**
     * 从 SAF 拿到的 Uri 导入一条 GPX。
     *
     * 全流程放 IO 线程：解析 + 几千点入库，主线程做会 ANR。
     * 失败原因一律转成人话（[ImportOutcome.Failure]），不把异常对象抛给 UI。
     */
    fun importGpx(uri: Uri, onDone: (ImportOutcome) -> Unit) {
        if (_importing.value) return
        viewModelScope.launch {
            _importing.value = true
            val outcome = withContext(Dispatchers.IO) { runImport(uri) }
            _importing.value = false
            onDone(outcome)
        }
    }

    private suspend fun runImport(uri: Uri): ImportOutcome {
        val parsed = try {
            val resolver = getApplication<Application>().contentResolver
            resolver.openInputStream(uri)?.use { GpxParser.parse(it) }
                ?: return ImportOutcome.Failure("打不开这个文件（可能已被移走或没有读取权限）")
        } catch (e: IllegalArgumentException) {
            // GpxParser 对 XML 错误抛的就是这个，消息已带行号
            return ImportOutcome.Failure(e.message ?: "GPX 解析失败")
        } catch (e: Exception) {
            return ImportOutcome.Failure("读取失败：${e.message ?: e.javaClass.simpleName}")
        }

        // 转换 + 距离累计全在 GpxImport 里（纯 Kotlin，有单测覆盖）：
        // 时间兜底、海拔兜底、1 秒间隔编造这些容易算错的细节不散在 ViewModel 里。
        val payload = GpxImport.toPayload(parsed, System.currentTimeMillis())
            ?: return ImportOutcome.Failure("文件里没有轨迹点（只支持 <trk>/<trkseg>/<trkpt> 结构）")

        return try {
            val id = repo.importTrack(
                name = payload.name,
                points = payload.points,
                startTime = payload.startTime,
                endTime = payload.endTime,
                distanceMeters = payload.distanceMeters
            )
            ImportOutcome.Success(id, payload.name, payload.points.size)
        } catch (e: Exception) {
            ImportOutcome.Failure("写入数据库失败：${e.message ?: e.javaClass.simpleName}")
        }
    }

    fun delete(sessionId: Long) {
        viewModelScope.launch { repo.deleteSession(sessionId) }
    }
}
