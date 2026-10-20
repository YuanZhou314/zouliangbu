package com.zouliangbu.footprint.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

/**
 * 本地库。
 *
 * **改表流程（硬约束，见 CLAUDE.md）**：
 *   1. 改 entity 后把 `version` +1
 *   2. 在 [MIGRATIONS] 里加一条 `Migration(old, new)`，写清 ALTER/建表 SQL
 *   3. 编译后 `app/schemas/` 会多出 `<new>.json`，**必须一起提交**
 *
 * `exportSchema = true` 是 2026-10-20（P2.1）打开的：v1 的 schema 就是那时固化下来的。
 * 没有它，改表时只能靠 `fallbackToDestructiveMigration` 清库——用户的轨迹会全丢。
 */
@Database(
    entities = [TrackSession::class, TrackPoint::class],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun sessionDao(): TrackSessionDao
    abstract fun pointDao(): TrackPointDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        /**
         * 迁移清单。Room 会从当前库版本逐级跑，缺一条就在启动时抛
         * `IllegalStateException: Room cannot verify the data integrity`。
         */
        private val MIGRATIONS: Array<Migration> = arrayOf()

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "zouliangbu.db"
                )
                    .addMigrations(*MIGRATIONS)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}