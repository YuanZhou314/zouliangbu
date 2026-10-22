package com.zouliangbu.footprint.ui.map

/**
 * 地图引擎抽象（ROADMAP P1.1）。
 *
 * 约定：
 *   - 所有坐标都是 **GCJ-02**（高德定位 SDK 的原始输出），引擎内部不做坐标系转换。
 *     GPX 导出时的 WGS-84 转换在 `util/CoordTransform` 里做，与地图无关。
 *   - 实现必须**复用覆盖物对象**（polyline / marker 只创建一次，之后只换点集），
 *     记录中每秒一个点，每次重建覆盖物会持续泄漏并掉帧。
 *   - 实现必须容忍"地图还没加载完就调用"：内部缓存最后一次设置，地图就绪后重放
 *     （见 `AmapMapEngine.reapply()`）。
 *
 * 当前实现：
 *   - [AmapMapEngine]  高德（主用）
 *   - osmdroid 分支**不实现**本接口（ROADMAP D1：只作 Key 失效兜底，不做功能对等）
 */
interface MapEngine {

    /** 全量设置轨迹折线。少于 2 个点时隐藏折线（单点画不出线，只靠蓝点）。 */
    fun setTrack(points: List<MapPoint>)

    /** 清空轨迹折线（不销毁对象，仅隐藏）。 */
    fun clearTrack()

    /** 起点 / 终点 marker；传 null 表示不显示该端。 */
    fun setEndpoints(start: MapPoint?, end: MapPoint?)

    /** 把相机缩放到刚好容纳整条轨迹。 */
    fun zoomToTrack(points: List<MapPoint>, paddingPx: Int = 96, coveredBottomPx: Int = 0)

    /** 移动相机到指定点。 */
    fun moveTo(point: MapPoint, zoom: Float = 16f, animate: Boolean = true)

    /**
     * 跟随模式：把相机平移到最新点，**保持当前缩放、不带平移动画**。
     * 记录中每秒调一次，带动画会一顿一顿；改缩放会让用户刚放大的视野被重置。
     */
    fun followTo(point: MapPoint)

    /** 切换底图类型。[type] 为 [MapType] 枚举，实现内部映射到厂商常量。 */
    fun setMapType(type: MapType)

    /** 放大一级。 */
    fun zoomIn()

    /** 缩小一级。 */
    fun zoomOut()
}

/** 底图类型，与厂商无关（高德 MAP_TYPE_* / osmdroid tile source）。 */
enum class MapType {
    /** 标准矢量地图。 */
    NORMAL,

    /** 卫星影像。 */
    SATELLITE
}
