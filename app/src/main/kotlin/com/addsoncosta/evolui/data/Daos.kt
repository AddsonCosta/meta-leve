package com.addsoncosta.evolui.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profile WHERE id = 1")
    fun observe(): Flow<ProfileEntity?>

    @Query("SELECT * FROM profile WHERE id = 1")
    suspend fun get(): ProfileEntity?

    @Upsert
    suspend fun save(profile: ProfileEntity)

    @Query("DELETE FROM profile")
    suspend fun clear()
}

@Dao
interface DayLogDao {
    @Query("SELECT * FROM day_log WHERE epochDay = :day")
    fun observe(day: Long): Flow<DayLogEntity?>

    @Query("SELECT * FROM day_log WHERE epochDay = :day")
    suspend fun get(day: Long): DayLogEntity?

    /** Último peso registrado até a data (inclusive). */
    @Query("SELECT weightKg FROM day_log WHERE epochDay <= :day AND weightKg IS NOT NULL ORDER BY epochDay DESC LIMIT 1")
    fun observeLatestWeight(day: Long): Flow<Double?>

    @Query("SELECT * FROM day_log WHERE weightKg IS NOT NULL ORDER BY epochDay")
    fun observeWeights(): Flow<List<DayLogEntity>>

    @Query("SELECT * FROM day_log")
    suspend fun all(): List<DayLogEntity>

    @Upsert
    suspend fun save(log: DayLogEntity)

    @Upsert
    suspend fun saveAll(logs: List<DayLogEntity>)

    @Query("DELETE FROM day_log")
    suspend fun clear()
}

@Dao
interface FoodDao {
    @Query("SELECT COUNT(*) FROM food")
    suspend fun count(): Int

    @Query(
        "SELECT * FROM food WHERE searchName LIKE '%' || :query || '%' " +
            "ORDER BY favorite DESC, (searchName LIKE :query || '%') DESC, name LIMIT 150"
    )
    fun search(query: String): Flow<List<FoodEntity>>

    @Query("SELECT * FROM food WHERE favorite = 1 ORDER BY name")
    fun observeFavorites(): Flow<List<FoodEntity>>

    @Query("SELECT * FROM food WHERE id = :id")
    suspend fun get(id: Long): FoodEntity?

    @Query("SELECT * FROM food WHERE custom = 1")
    suspend fun customFoods(): List<FoodEntity>

    @Query("SELECT tacoId FROM food WHERE favorite = 1 AND tacoId IS NOT NULL")
    suspend fun favoriteTacoIds(): List<Int>

    @Query("UPDATE food SET favorite = 1 WHERE tacoId IN (:ids)")
    suspend fun markTacoFavorites(ids: List<Int>)

    @Query("UPDATE food SET favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(food: FoodEntity): Long

    @Insert
    suspend fun insertAll(foods: List<FoodEntity>)

    @Delete
    suspend fun delete(food: FoodEntity)

    @Query("DELETE FROM food WHERE custom = 1")
    suspend fun clearCustom()

    @Query("UPDATE food SET favorite = 0")
    suspend fun clearFavorites()
}

@Dao
interface MealDao {
    @Query("SELECT * FROM meal WHERE epochDay = :day ORDER BY id")
    fun observeDay(day: Long): Flow<List<MealEntity>>

    @Query("SELECT epochDay, SUM(kcal) AS total FROM meal GROUP BY epochDay ORDER BY epochDay")
    fun observeDailyTotals(): Flow<List<DayTotal>>

    @Query("SELECT * FROM meal")
    suspend fun all(): List<MealEntity>

    @Insert
    suspend fun insert(meal: MealEntity)

    @Insert
    suspend fun insertAll(meals: List<MealEntity>)

    @Delete
    suspend fun delete(meal: MealEntity)

    @Query("DELETE FROM meal")
    suspend fun clear()
}

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

    @Query("DELETE FROM workout")
    suspend fun clear()
}
