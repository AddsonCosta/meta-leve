package com.addsoncosta.evolui.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.addsoncosta.evolui.core.Profile
import java.io.File
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

    suspend fun dayLogOnce(date: LocalDate): DayLogEntity? = db.dayLogDao().get(date.toEpochDay())

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

    /** Grava treinos vindos do Health Connect, ignorando os que já foram importados. Devolve quantos entraram. */
    suspend fun importWorkouts(workouts: List<WorkoutEntity>): Int {
        val known = db.workoutDao().externalIds().toSet()
        val fresh = workouts.filter { it.externalId != null && it.externalId !in known }
        if (fresh.isNotEmpty()) db.workoutDao().insertAll(fresh)
        return fresh.size
    }

    // Jejum

    fun fasts(): Flow<List<FastEntity>> = db.fastDao().observeAll()

    suspend fun saveFast(fast: FastEntity) = db.fastDao().save(fast)

    suspend fun deleteFast(fast: FastEntity) = db.fastDao().delete(fast)

    // Fotos de progresso, copiadas para a pasta interna do app (só ficam no celular).

    fun photos(): Flow<List<PhotoEntity>> = db.photoDao().observeAll()

    fun photoFile(photo: PhotoEntity): File = File(photoDir(), photo.fileName)

    suspend fun addPhoto(uri: Uri, date: LocalDate, weightKg: Double?) = withContext(Dispatchers.IO) {
        val name = "foto_${System.currentTimeMillis()}.jpg"
        context.contentResolver.openInputStream(uri)!!.use { input ->
            File(photoDir(), name).outputStream().use { input.copyTo(it) }
        }
        db.photoDao().insert(PhotoEntity(epochDay = date.toEpochDay(), fileName = name, weightKg = weightKg))
    }

    suspend fun deletePhoto(photo: PhotoEntity) = withContext(Dispatchers.IO) {
        photoFile(photo).delete()
        db.photoDao().delete(photo)
    }

    private fun photoDir() = File(context.filesDir, "fotos").apply { mkdirs() }

    /** Números de um período, para compartilhar. */
    suspend fun periodStats(from: LocalDate, to: LocalDate): PeriodStats = withContext(Dispatchers.IO) {
        val range = from.toEpochDay()..to.toEpochDay()
        val profile = db.profileDao().get()?.toProfile()
        val meals = db.mealDao().all().filter { it.epochDay in range }
        val workouts = db.workoutDao().all().filter { it.epochDay in range }
        val weights = db.dayLogDao().all().filter { it.weightKg != null }.sortedBy { it.epochDay }
        val zone = java.time.ZoneId.systemDefault()
        val fasts = db.fastDao().all().filter { f ->
            f.endMillis != null && java.time.Instant.ofEpochMilli(f.endMillis).atZone(zone).toLocalDate().toEpochDay() in range
        }
        // Peso no começo: o último registrado antes do período (ou o primeiro dentro dele).
        val before = weights.lastOrNull { it.epochDay < range.first }?.weightKg
            ?: weights.firstOrNull { it.epochDay in range }?.weightKg
            ?: profile?.startWeightKg
        val after = weights.lastOrNull { it.epochDay <= range.last }?.weightKg ?: before
        val loggedDays = meals.map { it.epochDay }.toSet()
        PeriodStats(
            from = from,
            to = to,
            name = profile?.name,
            daysLogged = loggedDays.size,
            totalEaten = meals.sumOf { it.kcal },
            avgEaten = if (loggedDays.isEmpty()) 0.0 else meals.sumOf { it.kcal } / loggedDays.size,
            workouts = workouts.size,
            workoutMinutes = workouts.sumOf { it.minutes },
            workoutKcal = workouts.sumOf { it.kcal },
            startWeight = before,
            endWeight = after,
            targetWeight = profile?.targetWeightKg,
            fasts = fasts.size,
            fastHours = fasts.sumOf { (it.endMillis!! - it.startMillis) / 3_600_000.0 },
            fastsOnTarget = fasts.count { (it.endMillis!! - it.startMillis) >= it.targetHours * 3_600_000L },
        )
    }

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
            fasts = db.fastDao().all(),
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
            db.fastDao().clear()

            backup.profile?.let { db.profileDao().save(it) }
            db.dayLogDao().saveAll(backup.days)
            backup.customFoods.forEach { db.foodDao().insert(it) }
            if (backup.favoriteTacoIds.isNotEmpty()) db.foodDao().markTacoFavorites(backup.favoriteTacoIds)
            backup.customExercises.forEach { db.exerciseDao().insert(it) }
            db.mealDao().insertAll(backup.meals)
            db.workoutDao().insertAll(backup.workouts)
            db.fastDao().insertAll(backup.fasts)
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
    val fasts: List<FastEntity> = emptyList(),
) {
    companion object {
        const val VERSION = 2
    }
}

data class PeriodStats(
    val from: LocalDate,
    val to: LocalDate,
    val name: String?,
    val daysLogged: Int,
    val totalEaten: Double,
    val avgEaten: Double,
    val workouts: Int,
    val workoutMinutes: Double,
    val workoutKcal: Double,
    val startWeight: Double?,
    val endWeight: Double?,
    val targetWeight: Double?,
    val fasts: Int,
    val fastHours: Double,
    val fastsOnTarget: Int,
) {
    /** Variação de peso no período (negativa = perdeu). */
    val weightChange: Double? get() = if (startWeight != null && endWeight != null) endWeight - startWeight else null
}
