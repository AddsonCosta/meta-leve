package app.metaleve.data

import android.content.Context
import androidx.room.withTransaction
import app.metaleve.core.Profile
import java.text.Normalizer
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class Repository(private val context: Context, private val db: AppDatabase) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    val profile: Flow<Profile?> = db.profileDao().observe().map { it?.toProfile() }

    suspend fun saveProfile(profile: Profile) = db.profileDao().save(ProfileEntity.from(profile))

    fun dayLog(date: LocalDate): Flow<DayLogEntity?> = db.dayLogDao().observe(date.toEpochDay())

    fun latestWeight(date: LocalDate): Flow<Double?> = db.dayLogDao().observeLatestWeight(date.toEpochDay())

    fun weights(): Flow<List<DayLogEntity>> = db.dayLogDao().observeWeights()

    suspend fun updateDay(date: LocalDate, change: (DayLogEntity) -> DayLogEntity) {
        val dao = db.dayLogDao()
        val day = date.toEpochDay()
        dao.save(change(dao.get(day) ?: DayLogEntity(epochDay = day)))
    }

    fun meals(date: LocalDate): Flow<List<MealEntity>> = db.mealDao().observeDay(date.toEpochDay())

    fun workouts(date: LocalDate): Flow<List<WorkoutEntity>> = db.workoutDao().observeDay(date.toEpochDay())

    fun dailyEaten(): Flow<List<DayTotal>> = db.mealDao().observeDailyTotals()

    fun dailyExercise(): Flow<List<DayTotal>> = db.workoutDao().observeDailyTotals()

    fun searchFoods(query: String): Flow<List<FoodEntity>> = db.foodDao().search(normalize(query))

    suspend fun setFoodFavorite(food: FoodEntity, favorite: Boolean) = db.foodDao().setFavorite(food.id, favorite)

    suspend fun saveCustomFood(food: FoodEntity): Long =
        db.foodDao().insert(food.copy(custom = true, searchName = normalize(food.name)))

    suspend fun deleteFood(food: FoodEntity) = db.foodDao().delete(food)

    suspend fun logFood(date: LocalDate, food: FoodEntity, grams: Double, mealType: MealType) {
        val f = grams / 100
        db.mealDao().insert(
            MealEntity(
                epochDay = date.toEpochDay(),
                foodId = food.id,
                name = food.name,
                grams = grams,
                mealType = mealType.name,
                kcal = food.kcal * f,
                protein = food.protein * f,
                fat = food.fat * f,
                carb = food.carb * f,
            )
        )
    }

    suspend fun deleteMeal(meal: MealEntity) = db.mealDao().delete(meal)

    fun exercises(): Flow<List<ExerciseEntity>> = db.exerciseDao().observeAll()

    suspend fun setExerciseFavorite(exercise: ExerciseEntity, favorite: Boolean) =
        db.exerciseDao().setFavorite(exercise.id, favorite)

    suspend fun saveCustomExercise(exercise: ExerciseEntity): Long = db.exerciseDao().insert(exercise.copy(custom = true))

    suspend fun deleteExercise(exercise: ExerciseEntity) = db.exerciseDao().delete(exercise)

    suspend fun logWorkout(date: LocalDate, exercise: ExerciseEntity, minutes: Double, kcal: Double) {
        db.workoutDao().insert(
            WorkoutEntity(epochDay = date.toEpochDay(), exerciseId = exercise.id, name = exercise.name, minutes = minutes, kcal = kcal)
        )
    }

    suspend fun deleteWorkout(workout: WorkoutEntity) = db.workoutDao().delete(workout)

    /** Carrega a TACO e os exercícios na primeira execução. */
    suspend fun seedIfNeeded() = withContext(Dispatchers.IO) {
        if (db.foodDao().count() == 0) {
            val foods = json.decodeFromString<List<TacoFood>>(readAsset("taco.json")).map {
                FoodEntity(
                    category = it.category,
                    name = it.name,
                    searchName = normalize(it.name),
                    kcal = it.kcal,
                    protein = it.protein,
                    fat = it.fat,
                    carb = it.carb,
                    fiber = it.fiber,
                    tacoId = it.id,
                )
            }
            db.foodDao().insertAll(foods)
        }
        if (db.exerciseDao().count() == 0) {
            val exercises = json.decodeFromString<List<SeedExercise>>(readAsset("exercises.json")).map {
                ExerciseEntity(category = it.category, name = it.name, met = it.met)
            }
            db.exerciseDao().insertAll(exercises)
        }
    }

    suspend fun exportBackup(): String = withContext(Dispatchers.IO) {
        val backup = Backup(
            version = Backup.VERSION,
            profile = db.profileDao().get(),
            days = db.dayLogDao().all(),
            customFoods = db.foodDao().customFoods(),
            favoriteTacoIds = db.foodDao().favoriteTacoIds(),
            meals = db.mealDao().all(),
            customExercises = db.exerciseDao().customExercises(),
            workouts = db.workoutDao().all(),
        )
        json.encodeToString(Backup.serializer(), backup)
    }

    /** Substitui os dados pessoais pelos do backup. O catálogo TACO é mantido. */
    suspend fun importBackup(text: String) = withContext(Dispatchers.IO) {
        val backup = json.decodeFromString(Backup.serializer(), text)
        db.withTransaction {
            db.profileDao().clear()
            db.dayLogDao().clear()
            db.mealDao().clear()
            db.workoutDao().clear()
            db.foodDao().clearCustom()
            db.foodDao().clearFavorites()
            db.exerciseDao().clearCustom()

            backup.profile?.let { db.profileDao().save(it) }
            db.dayLogDao().saveAll(backup.days)
            backup.customFoods.forEach { db.foodDao().insert(it) }
            if (backup.favoriteTacoIds.isNotEmpty()) db.foodDao().markTacoFavorites(backup.favoriteTacoIds)
            backup.customExercises.forEach { db.exerciseDao().insert(it) }
            db.mealDao().insertAll(backup.meals)
            db.workoutDao().insertAll(backup.workouts)
        }
    }

    private fun readAsset(name: String) = context.assets.open(name).bufferedReader().use { it.readText() }

    companion object {
        /** Minúsculas e sem acentos, para a busca encontrar "feijao" e "feijão". */
        fun normalize(text: String): String =
            Normalizer.normalize(text.trim().lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
    }
}

@Serializable
private data class TacoFood(
    val id: Int,
    val category: String,
    val name: String,
    val kcal: Double,
    val protein: Double,
    val fat: Double,
    val carb: Double,
    val fiber: Double,
)

@Serializable
private data class SeedExercise(val category: String, val name: String, val met: Double)

@Serializable
data class Backup(
    val version: Int,
    val profile: ProfileEntity?,
    val days: List<DayLogEntity>,
    val customFoods: List<FoodEntity>,
    val favoriteTacoIds: List<Int>,
    val meals: List<MealEntity>,
    val customExercises: List<ExerciseEntity>,
    val workouts: List<WorkoutEntity>,
) {
    companion object {
        const val VERSION = 1
    }
}
