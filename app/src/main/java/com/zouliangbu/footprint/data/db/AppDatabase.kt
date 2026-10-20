package com.zouliangbu.footprint.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun sessionDao(): TrackSessionDao
    abstract fun pointDao(): TrackPointDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        /** v2：轨迹名（导入 GPX 时用文件里的 <name>，自己记录的可空）。 */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 新列可空，老数据自动为 NULL，UI 按开始时间兜底，无需回填。
                db.execSQL("ALTER TABLE track_sessions ADD COLUMN name TEXT")
            }
        }

        /**
         * 迁移清单。Room 会从当前库版本逐级跑，缺一条就在启动时抛
         * `IllegalStateException: Room cannot verify the data integrity`。
         */
        private val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)

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
