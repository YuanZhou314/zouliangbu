package com.zouliangbu.footprint.data

import android.content.Context
import java.util.UUID

/**
 * 匿名设备标识（提案提交 / 投票的身份）。
 *
 * 为什么不用账号：开源项目里"注册才能贡献"会把贡献者挡在门外（见 docs/product-philosophy.md）。
 * 这里生成一次 UUID 存 SharedPreferences，只用于**防重复投票和防刷**，不含任何个人信息，
 * 也不会上传到除自家服务端以外的任何地方。
 *
 * 用户自愿注册账号后，昵称仍走 [com.zouliangbu.footprint.data.remote.AuthClient]，
 * 但提案身份依旧是这个 UUID —— 换手机就是新身份，这是有意的取舍：
 * 宁可丢一点防刷能力，也不要为了"跨设备同一人"逼用户登录。
 */
object DeviceId {

    private const val PREFS = "zlb_device"
    private const val KEY_UUID = "uuid"

    fun get(context: Context): String {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val existing = sp.getString(KEY_UUID, null)
        if (!existing.isNullOrBlank()) return existing
        val id = UUID.randomUUID().toString()
        sp.edit().putString(KEY_UUID, id).apply()
        return id
    }
}
