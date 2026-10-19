package com.zouliangbu.footprint.data

import android.content.Context
import com.zouliangbu.footprint.R

/**
 * 高德 (AMap) Android SDK API Key 的运行时读取与有效性判断。
 *
 * Key 实际值定义在 res/values/strings.xml 的 amap_api_key，
 * 由 AndroidManifest.xml 的 <meta-data android:name="com.amap.api.v2.apikey" /> 引用。
 * AMap Location SDK 和 3D Map SDK 都会自动从 manifest 读取。
 *
 * TODO 配置步骤：
 *   1. 访问 https://lbs.amap.com 注册高德开发者账号（个人/企业均可）。
 *   2. 控制台 → 应用管理 → 创建新应用 → 平台选择「Android」。
 *   3. 填入：
 *        Package Name  = com.zouliangbu.footprint
 *        SHA1          = 用 keytool 取 debug.keystore 的指纹：
 *                          keytool -list -v -keystore ~/.android/debug.keystore \
 *                                  -storepass android -alias androiddebug
 *                        或 Gradle 任务：./gradlew signingReport
 *   4. 把高德下发的 API Key 替换 res/values/strings.xml 中 amap_api_key 的占位符。
 *   5. 重新 build。MapView 即可加载真实瓦片；无 Key 时 App 仍可编译和安装，
 *      地图区域显示「请配置高德 Key」占位视图（不空白、不灰屏）。
 */
object AmapKey {

    private const val PLACEHOLDER = "REPLACE_WITH_YOUR_AMAP_KEY"

    fun apiKey(context: Context): String =
        runCatching { context.getString(R.string.amap_api_key) }.getOrDefault("")

    /** 判断当前是否填入了真实可用的高德 Key（避免在占位符情况下让 MapView 报错）。 */
    fun isConfigured(context: Context): Boolean {
        val key = apiKey(context)
        return key.isNotBlank() &&
                key != PLACEHOLDER &&
                key.length >= 20
    }
}