package com.addsoncosta.evolui.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

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
interface FoodPortionDao {
    @Query("SELECT * FROM food_portion WHERE foodId = :foodId ORDER BY grams")
    suspend fun forFood(foodId: Long): List<FoodPortionEntity>

    @Query("SELECT * FROM food_portion WHERE custom = 1")
    suspend fun customPortions(): List<FoodPortionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(portion: FoodPortionEntity): Long

    @Insert
    suspend fun insertAll(portions: List<FoodPortionEntity>)

    @Delete
    suspend fun delete(portion: FoodPortionEntity)

    @Query("DELETE FROM food_portion WHERE custom = 1")
    suspend fun clearCustom()
}

@Dao
interface SavedMealDao {
    @Query("SELECT * FROM saved_meal ORDER BY name")
    fun observeAll(): Flow<List<SavedMealEntity>>

    @Query("SELECT * FROM saved_meal")
    suspend fun all(): List<SavedMealEntity>

    @Query("SELECT * FROM saved_meal_item WHERE savedMealId = :mealId ORDER BY id")
    suspend fun items(mealId: Long): List<SavedMealItemEntity>

    @Query("SELECT * FROM saved_meal_item")
    suspend fun allItems(): List<SavedMealItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(meal: SavedMealEntity): Long

    @Insert
    suspend fun insertItems(items: List<SavedMealItemEntity>)

    @Query("DELETE FROM saved_meal_item WHERE savedMealId = :mealId")
    suspend fun deleteItems(mealId: Long)

    @Delete
    suspend fun delete(meal: SavedMealEntity)

    @Query("DELETE FROM saved_meal")
    suspend fun clear()

    @Query("DELETE FROM saved_meal_item")
    suspend fun clearItems()
}
