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
    entities = [TrackSession::class, TrackPoint::class, GearItem::class],
    version = 5,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun sessionDao(): TrackSessionDao
    abstract fun pointDao(): TrackPointDao
    abstract fun gearItemDao(): GearItemDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        /** v2：轨迹名（导入 GPX 时用文件里的 <name>，自己记录的可空）。 */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 新列可空，老数据自动为 NULL，UI 按开始时间兜底，无需回填。
                db.execSQL("ALTER TABLE track_sessions ADD COLUMN name TEXT")
            }
        }

        /** v3：累计暂停时长（P2.4）。老记录当作从未暂停，默认 0。 */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // NOT NULL + DEFAULT：SQLite 的 ALTER TABLE ADD COLUMN 要求非空列必须带默认值，
                // 老行自动填 0，正好等价于"这条记录没暂停过"。
                db.execSQL(
                    "ALTER TABLE track_sessions ADD COLUMN pausedMs INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        /** v4：累计爬升 / 下降（ROADMAP P3.1）。老记录没算过，一律 0（不猜）。 */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // NOT NULL + DEFAULT：SQLite 的 ALTER TABLE ADD COLUMN 要求非空列必须带默认值。
                // 用 REAL 而不是 FLOAT：SQLite 没有 FLOAT 类型，Room 映射 Double 用 REAL。
                db.execSQL(
                    "ALTER TABLE track_sessions ADD COLUMN ascentMeters REAL NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE track_sessions ADD COLUMN descentMeters REAL NOT NULL DEFAULT 0"
                )
            }
        }

        /**
         * v5：装备台账（ROADMAP 装备模块）。
         *
         * CREATE TABLE 的列顺序 / 类型 / NOT NULL / 默认值必须与 [GearItem] entity 逐列一致——
         * Room 迁移后会跑 schema 校验，差一个字启动就抛 IllegalStateException。
         * entity 没写 @ColumnInfo(defaultValue)，所以这里也不带 DEFAULT 子句
         * （Kotlin 默认值是应用层的，不进 schema）。
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `gear_items` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`name` TEXT NOT NULL, " +
                            "`brandModel` TEXT NOT NULL, " +
                            "`categoryId` TEXT NOT NULL, " +
                            "`status` INTEGER NOT NULL, " +
                            "`weightGrams` INTEGER NOT NULL, " +
                            "`priceCents` INTEGER, " +
                            "`purchaseDate` TEXT, " +
                            "`note` TEXT, " +
                            "`createdAt` INTEGER NOT NULL)"
                )
            }
        }

        /**
         * 迁移清单。Room 会从当前库版本逐级跑，缺一条就在启动时抛
         * `IllegalStateException: Room cannot verify the data integrity`。
         */
        private val MIGRATIONS: Array<Migration> =
            arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)

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
