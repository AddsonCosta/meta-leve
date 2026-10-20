package com.addsoncosta.evolui.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.addsoncosta.evolui.backup.MissingFiles
import com.addsoncosta.evolui.backup.OmittedFile
import com.addsoncosta.evolui.backup.pdfPageCount
import com.addsoncosta.evolui.core.Profile
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
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

    /** Fotos e anexos que não vieram no último backup restaurado (backup sem fotos e anexos). */
    val missingFiles = MissingFiles(context)

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
        // Correções e itens novos para quem já tinha o app instalado.
        FoodSeed.upgrade(context, db)
        ExerciseSeed.upgrade(context, db)
    }

    /**
     * Dados do backup em JSON. Com [includeFiles] falso, o .zip não leva fotos nem anexos e o JSON guarda
     * uma descrição de cada arquivo deixado de fora ([Backup.omittedFiles]).
     */
    suspend fun exportBackup(includeFiles: Boolean = true): String = withContext(Dispatchers.IO) {
        val backup = Backup(
            version = Backup.VERSION,
            includesFiles = includeFiles,
            omittedFiles = if (includeFiles) emptyList() else describeFiles(),
            profile = db.profileDao().get(),
            days = db.dayLogDao().all(),
            customFoods = db.foodDao().customFoods(),
            favoriteTacoIds = db.foodDao().favoriteTacoIds(),
            meals = db.mealDao().all(),
            customExercises = db.exerciseDao().customExercises(),
            workouts = db.workoutDao().all(),
            fasts = db.fastDao().all(),
            photos = db.photoDao().all(),
            customPortions = db.foodPortionDao().customPortions(),
            savedMeals = db.savedMealDao().all(),
            savedMealItems = db.savedMealDao().allItems(),
            badges = db.badgeDao().all(),
            healthRecords = db.healthRecordDao().all(),
            attachments = db.healthAttachmentDao().all(),
            labResults = db.labResultDao().all(),
            events = db.healthEventDao().all(),
            conditions = db.healthConditionDao().all(),
            teeth = db.toothStateDao().all(),
            cycles = db.menstrualCycleDao().all(),
            bloodPressure = db.bloodPressureDao().all(),
            routine = db.routineDao().all(),
        )
        json.encodeToString(Backup.serializer(), backup)
    }

    /**
     * Backup completo em .zip: os dados (backup.json) mais as fotos de progresso e os anexos da Saúde.
     */
    suspend fun exportBackupZip(out: OutputStream, includeFiles: Boolean = true) = withContext(Dispatchers.IO) {
        val text = exportBackup(includeFiles)
        ZipOutputStream(out.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(BACKUP_JSON))
            zip.write(text.toByteArray())
            zip.closeEntry()
            val dirs = if (includeFiles) listOf(photoDir(), File(context.filesDir, ANEXOS_DIR)) else emptyList()
            for (dir in dirs) {
                dir.listFiles()?.filter { it.isFile }?.forEach { file ->
                    zip.putNextEntry(ZipEntry("${dir.name}/${file.name}"))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
    }

    /** Descrição de cada foto de progresso e anexo da Saúde, para um backup sem os arquivos. */
    private suspend fun describeFiles(): List<OmittedFile> {
        val records = db.healthRecordDao().all().associateBy { it.id }
        val valuesByRecord = db.labResultDao().all().groupingBy { it.recordId }.eachCount()
        val anexos = File(context.filesDir, ANEXOS_DIR)
        val photos = db.photoDao().all().map { ph ->
            val file = File(photoDir(), ph.fileName)
            OmittedFile(
                kind = OmittedFile.KIND_PHOTO,
                folder = photoDir().name,
                fileName = ph.fileName,
                epochDay = ph.epochDay,
                photoId = ph.id,
                weightKg = ph.weightKg,
                sizeBytes = file.length().takeIf { file.exists() },
                photo = ph,
            )
        }
        val attachments = db.healthAttachmentDao().all().map { a ->
            val record = records[a.recordId]
            val file = File(anexos, a.fileName)
            val kind = OmittedFile.kindFor(a.mimeType)
            val typeName = record?.let { r -> HealthType.entries.firstOrNull { it.name == r.type }?.singular ?: "Registro" }
            OmittedFile(
                kind = kind,
                folder = ANEXOS_DIR,
                fileName = a.fileName,
                originalName = a.originalName,
                epochDay = record?.epochDay,
                addedMillis = a.addedMillis,
                recordId = a.recordId,
                recordLabel = record?.let { r -> if (r.title.isBlank()) typeName else "$typeName: ${r.title}" },
                pages = if (kind == OmittedFile.KIND_PDF && file.exists()) pdfPageCount(file) else null,
                valuesRead = valuesByRecord[a.recordId] ?: 0,
                readText = a.ocrText?.trim()?.take(OmittedFile.READ_TEXT_LIMIT)?.takeIf { it.isNotEmpty() },
                sizeBytes = file.length().takeIf { file.exists() },
            )
        }
        return photos + attachments
    }

    /**
     * Põe de volta o arquivo de uma foto ou anexo que não veio no backup: copia [uri] com o mesmo nome interno,
     * para o registro restaurado voltar a abrir o arquivo. Devolve falso se não conseguir ler.
     */
    suspend fun reattachFile(item: OmittedFile, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val name = File(item.fileName).name
        if (name.isBlank() || name.startsWith(".")) return@withContext false
        val dir = if (item.isPhoto) photoDir() else File(context.filesDir, ANEXOS_DIR).apply { mkdirs() }
        val target = File(dir, name)
        val copied = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input -> target.outputStream().use { input.copyTo(it) } } != null
        }.getOrDefault(false)
        if (!copied) return@withContext false
        if (item.isPhoto) {
            val photo = item.photo?.copy(fileName = name)
                ?: PhotoEntity(epochDay = item.epochDay ?: LocalDate.now().toEpochDay(), fileName = name, weightKg = item.weightKg)
            val exists = photo.id != 0L && db.photoDao().get(photo.id) != null
            if (!exists) {
                runCatching { db.photoDao().insert(photo) }.recoverCatching { db.photoDao().insert(photo.copy(id = 0)) }
            }
        } else {
            val mime = context.contentResolver.getType(uri)
            val recordId = item.recordId
            if (mime != null && recordId != null) {
                db.healthAttachmentDao().forRecord(recordId).firstOrNull { it.fileName == name }?.let { row ->
                    if (row.mimeType != mime) db.healthAttachmentDao().save(row.copy(mimeType = mime))
                }
            }
        }
        missingFiles.remove(item.fileName)
        true
    }

    /** Restaura um backup .zip (ou o .json antigo, se o arquivo não for zip). */
    suspend fun importBackupFile(input: () -> InputStream) = withContext(Dispatchers.IO) {
        val head = input().use { stream -> ByteArray(2).also { stream.read(it) } }
        val isZip = head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte()
        if (!isZip) {
            importBackup(input().bufferedReader().use { it.readText() })
            return@withContext
        }
        var text: String? = null
        val allowedDirs = setOf(photoDir().name, ANEXOS_DIR)
        ZipInputStream(input().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name
                if (name == BACKUP_JSON) {
                    text = zip.readBytes().decodeToString()
                } else if (!entry.isDirectory) {
                    // Só aceita arquivos simples dentro das pastas conhecidas (sem "../").
                    val parts = name.split('/')
                    if (parts.size == 2 && parts[0] in allowedDirs && parts[1].isNotBlank() && !parts[1].startsWith(".")) {
                        val dir = File(context.filesDir, parts[0]).apply { mkdirs() }
                        File(dir, File(parts[1]).name).outputStream().use { zip.copyTo(it) }
                    }
                }
                zip.closeEntry()
            }
        }
        importBackup(text ?: error("backup.json não encontrado"))
    }

    /** Substitui os dados pessoais pelos do backup. O catálogo de alimentos é mantido. */
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
            db.foodPortionDao().clearCustom()
            db.savedMealDao().clear()
            db.savedMealDao().clearItems()
            db.badgeDao().clear()
            db.healthRecordDao().clear()
            db.healthAttachmentDao().clear()
            db.labResultDao().clear()
            db.healthEventDao().clear()
            db.healthConditionDao().clear()
            db.toothStateDao().clear()
            db.menstrualCycleDao().clear()
            db.bloodPressureDao().clear()
            db.routineDao().clear()
            db.routineDao().clearAllSkips()
            // Fotos: backups antigos (só .json) não trazem as fotos; nesse caso mantém as atuais.
            if (backup.version >= 3) db.photoDao().clear()

            backup.profile?.let { db.profileDao().save(it) }
            db.dayLogDao().saveAll(backup.days)
            backup.customFoods.forEach { db.foodDao().insert(it) }
            if (backup.favoriteTacoIds.isNotEmpty()) db.foodDao().markTacoFavorites(backup.favoriteTacoIds)
            backup.customExercises.forEach { db.exerciseDao().insert(it) }
            db.mealDao().insertAll(backup.meals)
            db.workoutDao().insertAll(backup.workouts)
            db.fastDao().insertAll(backup.fasts)
            // Backup sem fotos: só volta a foto cujo arquivo já está no celular; as outras ficam em [missingFiles].
            if (backup.version >= 3) {
                db.photoDao().insertAll(if (backup.includesFiles) backup.photos else backup.photos.filter { photoFile(it).exists() })
            }
            db.foodPortionDao().insertAll(backup.customPortions)
            backup.savedMeals.forEach { db.savedMealDao().insert(it) }
            db.savedMealDao().insertItems(backup.savedMealItems)
            db.badgeDao().insertAll(backup.badges)
            db.healthRecordDao().insertAll(backup.healthRecords)
            db.healthAttachmentDao().insertAll(backup.attachments)
            db.labResultDao().insertAll(backup.labResults)
            db.healthEventDao().insertAll(backup.events)
            db.healthConditionDao().insertAll(backup.conditions)
            db.toothStateDao().saveAll(backup.teeth)
            db.menstrualCycleDao().insertAll(backup.cycles)
            db.bloodPressureDao().insertAll(backup.bloodPressure)
            db.routineDao().insertAll(backup.routine)
        }
        // Arquivos que ficaram de fora do backup e não existem neste celular: o registro mostra o aviso para anexar de novo.
        missingFiles.replaceAll(
            if (backup.includesFiles) emptyList()
            else backup.omittedFiles.filter { item ->
                val name = File(item.fileName).name
                name.isNotBlank() && !File(if (item.isPhoto) photoDir() else File(context.filesDir, ANEXOS_DIR), name).exists()
            },
        )
    }

    private fun readAsset(name: String) = context.assets.open(name).bufferedReader().use { it.readText() }

    companion object {
        /** Minúsculas e sem acentos, para a busca encontrar "feijao" e "feijão". */
        const val BACKUP_JSON = "backup.json"
        const val ANEXOS_DIR = "anexos"

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
    val photos: List<PhotoEntity> = emptyList(),
    val customPortions: List<FoodPortionEntity> = emptyList(),
    val savedMeals: List<SavedMealEntity> = emptyList(),
    val savedMealItems: List<SavedMealItemEntity> = emptyList(),
    val badges: List<BadgeEntity> = emptyList(),
    val healthRecords: List<HealthRecordEntity> = emptyList(),
    val attachments: List<HealthAttachmentEntity> = emptyList(),
    val labResults: List<LabResultEntity> = emptyList(),
    val events: List<HealthEventEntity> = emptyList(),
    val conditions: List<HealthConditionEntity> = emptyList(),
    val teeth: List<ToothStateEntity> = emptyList(),
    val cycles: List<MenstrualCycleEntity> = emptyList(),
    val bloodPressure: List<BloodPressureEntity> = emptyList(),
    /** Rotina do dia a dia (versão 0.5.0 em diante). */
    val routine: List<RoutineEntryEntity> = emptyList(),
    /** Falso quando o .zip foi feito "sem fotos e anexos" (versão 4 em diante). */
    val includesFiles: Boolean = true,
    /** Descrição de cada foto ou anexo deixado de fora (só quando [includesFiles] é falso). */
    val omittedFiles: List<OmittedFile> = emptyList(),
) {
    companion object {
        const val VERSION = 4
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
