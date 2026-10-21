package com.zouliangbu.server.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 系统配置 KV。用于"后台改、App 读"的开关，避免每加一个开关就 ALTER TABLE。
 *
 * 只放<b>全局单值</b>类配置（强更、公告）。列表类数据（分组、装备）不放这里 ——
 * KV 里塞 JSON 数组会让运营无法按行编辑。
 */
@TableName("app_config")
public class AppConfigEntity {

    @TableId(type = IdType.INPUT)
    private String configKey;
    private String configValue;
    private String note;
    private LocalDateTime updatedAt;

    public String getConfigKey() { return configKey; }
    public void setConfigKey(String configKey) { this.configKey = configKey; }

    public String getConfigValue() { return configValue; }
    public void setConfigValue(String configValue) { this.configValue = configValue; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
