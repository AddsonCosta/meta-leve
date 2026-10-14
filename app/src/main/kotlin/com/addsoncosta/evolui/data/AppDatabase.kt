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
    ],
    version = 2,
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

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "evolui.db").addMigrations(MIGRATION_1_2).build()

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
    }
}
