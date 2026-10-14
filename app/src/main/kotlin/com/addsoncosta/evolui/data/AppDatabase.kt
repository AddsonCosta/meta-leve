package com.addsoncosta.evolui.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ProfileEntity::class,
        DayLogEntity::class,
        FoodEntity::class,
        MealEntity::class,
        ExerciseEntity::class,
        WorkoutEntity::class,
        FastEntity::class,
        PhotoEntity::class,
        FoodPortionEntity::class,
        SavedMealEntity::class,
        SavedMealItemEntity::class,
        BadgeEntity::class,
        HealthRecordEntity::class,
        HealthAttachmentEntity::class,
        LabResultEntity::class,
        HealthEventEntity::class,
        HealthConditionEntity::class,
        ToothStateEntity::class,
        MenstrualCycleEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun dayLogDao(): DayLogDao
    abstract fun foodDao(): FoodDao
    abstract fun mealDao(): MealDao
    abstract fun exerciseDao(): ExerciseDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun fastDao(): FastDao
    abstract fun photoDao(): PhotoDao
    abstract fun foodPortionDao(): FoodPortionDao
    abstract fun savedMealDao(): SavedMealDao
    abstract fun badgeDao(): BadgeDao
    abstract fun healthRecordDao(): HealthRecordDao
    abstract fun healthAttachmentDao(): HealthAttachmentDao
    abstract fun labResultDao(): LabResultDao
    abstract fun healthEventDao(): HealthEventDao
    abstract fun healthConditionDao(): HealthConditionDao
    abstract fun toothStateDao(): ToothStateDao
    abstract fun menstrualCycleDao(): MenstrualCycleDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "evolui.db").addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()

        /** Versão 2: jejum, fotos de progresso e treinos importados do Health Connect. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `workout` ADD COLUMN `externalId` TEXT")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `fast` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`startMillis` INTEGER NOT NULL, `endMillis` INTEGER, `targetHours` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `photo` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`epochDay` INTEGER NOT NULL, `fileName` TEXT NOT NULL, `weightKg` REAL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_photo_epochDay` ON `photo` (`epochDay`)")
            }
        }

        /** Versão 3: medidas caseiras, refeições salvas, água, badges e a seção Saúde. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                fun add(table: String, column: String, type: String) =
                    db.execSQL("ALTER TABLE `$table` ADD COLUMN `$column` $type")
                add("food", "densityGPerMl", "REAL")
                add("food", "source", "TEXT")
                add("meal", "amount", "REAL")
                add("meal", "unit", "TEXT")
                add("day_log", "waterMl", "REAL")
                add("workout", "source", "TEXT")
                add("workout", "startMillis", "INTEGER")
                add("workout", "endMillis", "INTEGER")
                add("workout", "hcWrittenId", "TEXT")
                add("photo", "scale", "REAL")
                add("photo", "offsetX", "REAL")
                add("photo", "offsetY", "REAL")
                add("photo", "caption", "TEXT")
                CREATE_V3.forEach(db::execSQL)
            }
        }

        /** Tabelas novas da versão 3, no mesmo formato que o Room gera. */
        private val CREATE_V3 = listOf(
            "CREATE TABLE IF NOT EXISTS `food_portion` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `foodId` INTEGER NOT NULL, " +
                "`name` TEXT NOT NULL, `grams` REAL NOT NULL, `custom` INTEGER NOT NULL)",
            "CREATE INDEX IF NOT EXISTS `index_food_portion_foodId` ON `food_portion` (`foodId`)",
            "CREATE TABLE IF NOT EXISTS `saved_meal` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                "`mealType` TEXT NOT NULL, `createdMillis` INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS `saved_meal_item` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `savedMealId` INTEGER NOT NULL, " +
                "`foodId` INTEGER, `name` TEXT NOT NULL, `grams` REAL NOT NULL, `kcal` REAL NOT NULL, `protein` REAL NOT NULL, " +
                "`fat` REAL NOT NULL, `carb` REAL NOT NULL, `amount` REAL, `unit` TEXT)",
            "CREATE INDEX IF NOT EXISTS `index_saved_meal_item_savedMealId` ON `saved_meal_item` (`savedMealId`)",
            "CREATE TABLE IF NOT EXISTS `badge` (`key` TEXT NOT NULL, `earnedMillis` INTEGER NOT NULL, PRIMARY KEY(`key`))",
            "CREATE TABLE IF NOT EXISTS `health_record` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `type` TEXT NOT NULL, " +
                "`epochDay` INTEGER NOT NULL, `title` TEXT NOT NULL, `professional` TEXT, `place` TEXT, `description` TEXT, " +
                "`status` TEXT, `extra` TEXT, `createdMillis` INTEGER NOT NULL, `updatedMillis` INTEGER NOT NULL)",
            "CREATE INDEX IF NOT EXISTS `index_health_record_type` ON `health_record` (`type`)",
            "CREATE INDEX IF NOT EXISTS `index_health_record_epochDay` ON `health_record` (`epochDay`)",
            "CREATE TABLE IF NOT EXISTS `health_attachment` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `recordId` INTEGER NOT NULL, " +
                "`fileName` TEXT NOT NULL, `mimeType` TEXT NOT NULL, `originalName` TEXT, `ocrText` TEXT, `addedMillis` INTEGER NOT NULL)",
            "CREATE INDEX IF NOT EXISTS `index_health_attachment_recordId` ON `health_attachment` (`recordId`)",
            "CREATE TABLE IF NOT EXISTS `lab_result` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `recordId` INTEGER NOT NULL, " +
                "`epochDay` INTEGER NOT NULL, `analyte` TEXT NOT NULL, `value` REAL NOT NULL, `unit` TEXT, `refLow` REAL, " +
                "`refHigh` REAL, `refText` TEXT)",
            "CREATE INDEX IF NOT EXISTS `index_lab_result_recordId` ON `lab_result` (`recordId`)",
            "CREATE INDEX IF NOT EXISTS `index_lab_result_analyte` ON `lab_result` (`analyte`)",
            "CREATE TABLE IF NOT EXISTS `health_event` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `type` TEXT NOT NULL, " +
                "`epochDay` INTEGER NOT NULL, `minuteOfDay` INTEGER, `title` TEXT NOT NULL, `place` TEXT, `notes` TEXT, " +
                "`remindMinutesBefore` INTEGER, `done` INTEGER NOT NULL, `recordId` INTEGER)",
            "CREATE INDEX IF NOT EXISTS `index_health_event_epochDay` ON `health_event` (`epochDay`)",
            "CREATE TABLE IF NOT EXISTS `health_condition` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                "`status` TEXT NOT NULL, `sinceText` TEXT, `notes` TEXT)",
            "CREATE TABLE IF NOT EXISTS `tooth_state` (`number` INTEGER NOT NULL, `status` TEXT NOT NULL, `notes` TEXT, " +
                "`updatedEpochDay` INTEGER, PRIMARY KEY(`number`))",
            "CREATE TABLE IF NOT EXISTS `menstrual_cycle` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `startEpochDay` INTEGER NOT NULL, " +
                "`endEpochDay` INTEGER, `flow` TEXT, `symptoms` TEXT, `notes` TEXT)",
            "CREATE INDEX IF NOT EXISTS `index_menstrual_cycle_startEpochDay` ON `menstrual_cycle` (`startEpochDay`)",
        )
    }
}
