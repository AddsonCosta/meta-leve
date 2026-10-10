package com.addsoncosta.evolui.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ProfileEntity::class,
        DayLogEntity::class,
        FoodEntity::class,
        MealEntity::class,
        ExerciseEntity::class,
        WorkoutEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun dayLogDao(): DayLogDao
    abstract fun foodDao(): FoodDao
    abstract fun mealDao(): MealDao
    abstract fun exerciseDao(): ExerciseDao
    abstract fun workoutDao(): WorkoutDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "evolui.db").build()
    }
}
