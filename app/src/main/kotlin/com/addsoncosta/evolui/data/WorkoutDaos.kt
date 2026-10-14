package com.addsoncosta.evolui.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {
    @Query("SELECT COUNT(*) FROM exercise")
    suspend fun count(): Int

    @Query("SELECT * FROM exercise ORDER BY favorite DESC, category, name")
    fun observeAll(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercise WHERE custom = 1")
    suspend fun customExercises(): List<ExerciseEntity>

    @Query("UPDATE exercise SET favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(exercise: ExerciseEntity): Long

    @Insert
    suspend fun insertAll(exercises: List<ExerciseEntity>)

    @Delete
    suspend fun delete(exercise: ExerciseEntity)

    @Query("DELETE FROM exercise WHERE custom = 1")
    suspend fun clearCustom()
}

@Dao
interface WorkoutDao {
    @Query("SELECT * FROM workout WHERE epochDay = :day ORDER BY id")
    fun observeDay(day: Long): Flow<List<WorkoutEntity>>

    @Query("SELECT epochDay, SUM(kcal) AS total FROM workout GROUP BY epochDay ORDER BY epochDay")
    fun observeDailyTotals(): Flow<List<DayTotal>>

    @Query("SELECT * FROM workout")
    suspend fun all(): List<WorkoutEntity>

    @Insert
    suspend fun insert(workout: WorkoutEntity)

    @Insert
    suspend fun insertAll(workouts: List<WorkoutEntity>)

    @Delete
    suspend fun delete(workout: WorkoutEntity)

    @Query("SELECT externalId FROM workout WHERE externalId IS NOT NULL")
    suspend fun externalIds(): List<String>

    @Query("DELETE FROM workout")
    suspend fun clear()
}

