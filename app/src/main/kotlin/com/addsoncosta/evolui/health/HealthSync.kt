package com.addsoncosta.evolui.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Energy
import com.addsoncosta.evolui.core.EnergyCalculator
import com.addsoncosta.evolui.data.WorkoutEntity
import java.text.Normalizer
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** O que o Health Connect (Samsung Health, Strava e outros apps) tem para um dia. */
data class HealthDay(
    val steps: Long,
    val activeKcal: Double,
    /** Treinos do dia, de todas as origens, já sem duplicados entre apps. */
    val workouts: List<WorkoutEntity>,
    val latestWeightKg: Double?,
)

/** Treino importado que o usuário apagou no Evolui (não deve voltar). */
data class IgnoredWorkout(val id: String, val startMillis: Long?, val endMillis: Long?)

/**
 * Ligação com o Health Connect. Lê passos, calorias, treinos e peso de todos os apps que mandam
 * dados para lá (Samsung Health, Strava, Google Fit, Garmin...) e, se o usuário quiser,
 * grava lá os treinos lançados no Evolui.
 * Tudo protegido: sem Health Connect ou sem permissão, as funções devolvem vazio/nulo.
 */
class HealthSync(private val context: Context) {
    private val readSteps = HealthPermission.getReadPermission(StepsRecord::class)
    private val readKcal = HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class)
    private val readExercise = HealthPermission.getReadPermission(ExerciseSessionRecord::class)
    private val readWeight = HealthPermission.getReadPermission(WeightRecord::class)
    private val writeExercise = HealthPermission.getWritePermission(ExerciseSessionRecord::class)
    private val writeKcal = HealthPermission.getWritePermission(ActiveCaloriesBurnedRecord::class)

    /** Permissões de leitura (bastam para o app considerar "conectado"). */
    val readPermissions: Set<String> = setOf(readSteps, readKcal, readExercise, readWeight)

    /** Permissões para gravar os treinos do Evolui no Health Connect. */
    val writePermissions: Set<String> = setOf(writeExercise, writeKcal)

    /** Tudo o que o Evolui pede (leitura + escrita). O usuário pode dar só uma parte. */
    val permissions: Set<String> = readPermissions + writePermissions

    val available: Boolean get() = HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    /** Health Connect não instalado, mas pode ser instalado pela Play Store. */
    val needsInstall: Boolean
        get() = HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED

    fun permissionContract() = PermissionController.createRequestPermissionResultContract()

    private val client by lazy { HealthConnectClient.getOrCreate(context) }

    private val prefs = context.getSharedPreferences("configuracoes", Context.MODE_PRIVATE)

    /** Permissões já dadas; vazio se o Health Connect não existir ou der erro. */
    suspend fun grantedPermissions(): Set<String> {
        if (!available) return emptySet()
        return attempt { client.permissionController.getGrantedPermissions() } ?: emptySet()
    }

    /** Pode ler pelo menos um tipo de dado. */
    suspend fun hasReadPermissions(): Boolean = grantedPermissions().any { it in readPermissions }

    /** Pode gravar treinos e calorias. */
    suspend fun hasWritePermissions(): Boolean = grantedPermissions().containsAll(writePermissions)

    /** Mantido para compatibilidade: "conectado" = pode ler alguma coisa. */
    suspend fun hasPermissions(): Boolean = hasReadPermissions()

    /**
     * Lê um dia: passos, calorias ativas, treinos (sem duplicados) e o peso mais recente.
     * [restingKcalPerDay] (taxa de repouso da pessoa) melhora a estimativa de treinos que vêm sem calorias.
     */
    suspend fun readDay(date: LocalDate, bodyWeightKg: Double, restingKcalPerDay: Double? = null): HealthDay =
        withContext(Dispatchers.IO) {
            val granted = grantedPermissions()
            val zone = ZoneId.systemDefault()
            val start = date.atStartOfDay(zone).toInstant()
            val end = date.plusDays(1).atStartOfDay(zone).toInstant()
            val day = TimeRangeFilter.between(start, end)

            val metrics = mutableSetOf<AggregateMetric<*>>()
            if (readSteps in granted) metrics += StepsRecord.COUNT_TOTAL
            if (readKcal in granted) metrics += ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL
            val totals = if (metrics.isEmpty()) null else attempt {
                client.aggregate(AggregateRequest(metrics = metrics, timeRangeFilter = day))
            }

            val workouts = if (readExercise in granted) {
                sessionsAsWorkouts(start, end, granted, bodyWeightKg, restingKcalPerDay)
            } else {
                emptyList()
            }

            val latestWeight = if (readWeight in granted) attempt {
                client.readRecords(
                    ReadRecordsRequest(WeightRecord::class, timeRangeFilter = TimeRangeFilter.between(start.minus(Duration.ofDays(30)), end))
                ).records.maxByOrNull { it.time }?.takeIf { !it.time.isBefore(start) }?.weight?.inKilograms
            } else {
                null
            }

            HealthDay(
                steps = totals?.get(StepsRecord.COUNT_TOTAL) ?: 0L,
                activeKcal = totals?.get(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)?.inKilocalories ?: 0.0,
                workouts = workouts,
                latestWeightKg = latestWeight,
            )
        }

    /** Treinos de um período (ex.: a última semana), já sem duplicados. */
    suspend fun readWorkouts(from: LocalDate, to: LocalDate, bodyWeightKg: Double, restingKcalPerDay: Double? = null): List<WorkoutEntity> =
        withContext(Dispatchers.IO) {
            val granted = grantedPermissions()
            if (readExercise !in granted) return@withContext emptyList()
            val zone = ZoneId.systemDefault()
            sessionsAsWorkouts(
                from.atStartOfDay(zone).toInstant(),
                to.plusDays(1).atStartOfDay(zone).toInstant(),
                granted,
                bodyWeightKg,
                restingKcalPerDay,
            )
        }

    private class Candidate(val workout: WorkoutEntity, val measuredKcal: Boolean, val hasTitle: Boolean)

    private suspend fun sessionsAsWorkouts(
        start: Instant,
        end: Instant,
        granted: Set<String>,
        bodyWeightKg: Double,
        restingKcalPerDay: Double?,
    ): List<WorkoutEntity> {
        val sessions = attempt { readSessions(start, end) } ?: return emptyList()
        val own = context.packageName
        val zone = ZoneId.systemDefault()
        val canKcal = readKcal in granted
        val candidates = sessions
            // Nunca importa de volta o que o próprio Evolui gravou.
            .filter { it.metadata.dataOrigin.packageName != own }
            // Cada treino pertence ao dia em que começou.
            .filter { !it.startTime.isBefore(start) && it.startTime.isBefore(end) && it.endTime.isAfter(it.startTime) }
            .map { session ->
                val minutes = (Duration.between(session.startTime, session.endTime).toMillis() / 60_000.0)
                    .let { Math.round(it).toDouble() }.coerceAtLeast(1.0)
                val origin = session.metadata.dataOrigin
                // Calorias gravadas pelo mesmo app do treino (somar todos os apps contaria em dobro).
                val measured = if (canKcal) attempt {
                    client.aggregate(
                        AggregateRequest(
                            metrics = setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL),
                            timeRangeFilter = TimeRangeFilter.between(session.startTime, session.endTime),
                            dataOriginFilter = setOf(origin),
                        )
                    )[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories
                }?.takeIf { it > 0 } else null
                val met = metForType(session.exerciseType)
                val estimated = if (restingKcalPerDay != null && restingKcalPerDay > 0) {
                    EnergyCalculator.personalExerciseKcal(met, minutes, restingKcalPerDay)
                } else {
                    EnergyCalculator.exerciseKcal(met, bodyWeightKg, minutes)
                }
                val title = session.title?.trim()?.takeIf { it.isNotEmpty() }
                Candidate(
                    workout = WorkoutEntity(
                        epochDay = session.startTime.atZone(zone).toLocalDate().toEpochDay(),
                        exerciseId = null,
                        name = title ?: exerciseName(session.exerciseType),
                        minutes = minutes,
                        kcal = measured ?: estimated,
                        externalId = session.metadata.id,
                        source = origin.packageName,
                        startMillis = session.startTime.toEpochMilli(),
                        endMillis = session.endTime.toEpochMilli(),
                    ),
                    measuredKcal = measured != null,
                    hasTitle = title != null,
                )
            }
        return dedupe(candidates).map { it.workout }
    }

    /** Lê as sessões de exercício de todos os apps, página por página (limite de segurança). */
    private suspend fun readSessions(start: Instant, end: Instant): List<ExerciseSessionRecord> {
        val out = mutableListOf<ExerciseSessionRecord>()
        var token: String? = null
        var pages = 0
        do {
            val response = client.readRecords(
                ReadRecordsRequest(ExerciseSessionRecord::class, timeRangeFilter = TimeRangeFilter.between(start, end), pageToken = token)
            )
            out += response.records
            token = response.pageToken
            pages++
        } while (token != null && pages < 10)
        return out
    }

    /** O mesmo treino vindo de dois apps vira um só: fica o que tem calorias, depois título, depois o app preferido. */
    private fun dedupe(list: List<Candidate>): List<Candidate> {
        val ranked = list.sortedByDescending { score(it) }
        val kept = mutableListOf<Candidate>()
        for (c in ranked) {
            val w = c.workout
            val duplicate = kept.any { k ->
                sameWorkout(k.workout.startMillis ?: 0, k.workout.endMillis ?: 0, w.startMillis ?: 0, w.endMillis ?: 0)
            }
            if (!duplicate) kept += c
        }
        return kept.sortedBy { it.workout.startMillis ?: 0 }
    }

    private fun score(c: Candidate): Double {
        val source = c.workout.source.orEmpty()
        val index = PREFERRED_SOURCES.indexOfFirst { it in source }
        val sourceBonus = if (index < 0) 0.0 else (PREFERRED_SOURCES.size - index).toDouble() / (PREFERRED_SOURCES.size + 1)
        return (if (c.measuredKcal) 4.0 else 0.0) + (if (c.hasTitle) 2.0 else 0.0) + sourceBonus
    }

    // Gravação dos treinos do Evolui

    /**
     * Grava um treino do Evolui no Health Connect (sessão + calorias), para aparecer no Samsung Health.
     * Devolve o id da sessão gravada, ou nulo se não tiver permissão ou der erro.
     */
    suspend fun writeWorkout(name: String, exerciseType: Int, start: Instant, end: Instant, kcal: Double): String? {
        if (!end.isAfter(start)) return null
        val granted = grantedPermissions()
        if (writeExercise !in granted) return null
        val sessionId = attempt {
            client.insertRecords(listOf(sessionRecord(name, exerciseType, start, end))).recordIdsList.firstOrNull()
        } ?: return null
        if (writeKcal in granted && kcal > 0) {
            attempt { client.insertRecords(listOf(caloriesRecord(kcal, start, end, CALORIES_PREFIX + sessionId))) }
        }
        return sessionId
    }

    /** Apaga do Health Connect um treino que o Evolui gravou (sessão e calorias). */
    suspend fun deleteWorkout(sessionId: String): Boolean {
        val granted = grantedPermissions()
        if (writeExercise !in granted) return false
        val ok = attempt {
            client.deleteRecords(ExerciseSessionRecord::class, listOf(sessionId), emptyList())
            true
        } ?: false
        if (writeKcal in granted) {
            attempt { client.deleteRecords(ActiveCaloriesBurnedRecord::class, emptyList(), listOf(CALORIES_PREFIX + sessionId)) }
        }
        return ok
    }

    // Registros do Health Connect. Na versão 1.1.0 os construtores exigem `metadata`;
    // Metadata.manualEntry() marca o registro como lançado à mão pelo usuário.

    private fun sessionRecord(name: String, type: Int, start: Instant, end: Instant): ExerciseSessionRecord {
        val rules = ZoneId.systemDefault().rules
        return ExerciseSessionRecord(
            startTime = start,
            startZoneOffset = rules.getOffset(start),
            endTime = end,
            endZoneOffset = rules.getOffset(end),
            metadata = Metadata.manualEntry(),
            exerciseType = type,
            title = name,
        )
    }

    private fun caloriesRecord(kcal: Double, start: Instant, end: Instant, clientRecordId: String): ActiveCaloriesBurnedRecord {
        val rules = ZoneId.systemDefault().rules
        return ActiveCaloriesBurnedRecord(
            startTime = start,
            startZoneOffset = rules.getOffset(start),
            endTime = end,
            endZoneOffset = rules.getOffset(end),
            energy = Energy.kilocalories(kcal),
            metadata = Metadata.manualEntry(clientRecordId = clientRecordId),
        )
    }

    // Treinos importados que o usuário apagou

    /** Guarda um treino importado que foi apagado, para não voltar na próxima sincronização. */
    fun ignoreWorkout(id: String, startMillis: Long?, endMillis: Long?) {
        val set = prefs.getStringSet(KEY_IGNORED, null).orEmpty().toMutableSet()
        set += "$id|${startMillis ?: ""}|${endMillis ?: ""}"
        // Mantém só os mais recentes, para a lista não crescer sem fim.
        val trimmed = if (set.size <= MAX_IGNORED) set else {
            set.map { it to parseIgnored(it) }.sortedByDescending { it.second?.startMillis ?: 0 }.take(MAX_IGNORED).map { it.first }.toSet()
        }
        prefs.edit().putStringSet(KEY_IGNORED, trimmed).apply()
    }

    fun ignoredWorkouts(): List<IgnoredWorkout> =
        prefs.getStringSet(KEY_IGNORED, null).orEmpty().mapNotNull { parseIgnored(it) }

    private fun parseIgnored(text: String): IgnoredWorkout? {
        val parts = text.split('|')
        if (parts.size < 3) return null
        val id = parts.dropLast(2).joinToString("|")
        if (id.isEmpty()) return null
        return IgnoredWorkout(id, parts[parts.size - 2].toLongOrNull(), parts.last().toLongOrNull())
    }

    companion object {
        private const val KEY_IGNORED = "treinos_ignorados"
        private const val MAX_IGNORED = 500
        private const val CALORIES_PREFIX = "evolui-kcal-"
        private const val MINUTE = 60_000L

        /** Apps preferidos quando o mesmo treino chega de mais de um (o primeiro é o mais preferido). */
        private val PREFERRED_SOURCES = listOf("strava", "garmin", "polar", "shealth", "samsung", "fitbit", "google.android.apps.fitness")

        /**
         * Dois intervalos são o mesmo treino quando se sobrepõem em pelo menos 65% do menor,
         * ou começam com até 5 min de diferença e têm duração parecida.
         */
        fun sameWorkout(aStart: Long, aEnd: Long, bStart: Long, bEnd: Long): Boolean {
            val durA = (aEnd - aStart).coerceAtLeast(MINUTE)
            val durB = (bEnd - bStart).coerceAtLeast(MINUTE)
            val overlap = (minOf(aEnd, bEnd) - maxOf(aStart, bStart)).coerceAtLeast(0L)
            if (overlap >= 0.65 * minOf(durA, durB)) return true
            val closeStart = abs(aStart - bStart) <= 5 * MINUTE
            val similar = abs(durA - durB) <= maxOf(5 * MINUTE, (0.25 * maxOf(durA, durB)).toLong())
            return closeStart && similar
        }

        /** Tipo de exercício do Health Connect pelo nome/categoria do Evolui (padrão: outro treino). */
        fun exerciseTypeFor(name: String, category: String? = null): Int {
            val n = normalize(name)
            val c = normalize(category.orEmpty())
            fun has(vararg words: String) = words.any { it in n }
            return when {
                has("esteira") && has("corr", "trote") -> ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL
                has("corr", "trote") -> ExerciseSessionRecord.EXERCISE_TYPE_RUNNING
                has("trilha") -> ExerciseSessionRecord.EXERCISE_TYPE_HIKING
                has("caminh", "marcha", "passear", "esteira") -> ExerciseSessionRecord.EXERCISE_TYPE_WALKING
                has("ergometrica", "spinning") -> ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY
                has("bicicleta", "bike", "ciclismo", "bmx", "pedal") -> ExerciseSessionRecord.EXERCISE_TYPE_BIKING
                has("hidro") -> ExerciseSessionRecord.EXERCISE_TYPE_EXERCISE_CLASS
                has("polo aquatico") -> ExerciseSessionRecord.EXERCISE_TYPE_WATER_POLO
                has("natacao", "nado") && has("mar", "lago") -> ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER
                has("natacao", "nado") -> ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL
                has("remo ergometro") -> ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE
                has("remo") -> ExerciseSessionRecord.EXERCISE_TYPE_ROWING
                has("caiaque", "canoa", "paddle") -> ExerciseSessionRecord.EXERCISE_TYPE_PADDLING
                has("surf", "bodyboard") -> ExerciseSessionRecord.EXERCISE_TYPE_SURFING
                has("mergulho") -> ExerciseSessionRecord.EXERCISE_TYPE_SCUBA_DIVING
                has("musculacao", "powerlifting", "peso", "kettlebell", "elastico") -> ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING
                has("calistenia", "abdominais", "polichinelo", "flexoes") -> ExerciseSessionRecord.EXERCISE_TYPE_CALISTHENICS
                has("hiit", "tabata", "burpee", "crossfit") -> ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING
                has("funcional", "circuito", "bootcamp") -> ExerciseSessionRecord.EXERCISE_TYPE_BOOT_CAMP
                has("eliptico") -> ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL
                has("stepper", "simulador de escada") -> ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING_MACHINE
                has("escada") -> ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING
                has("step", "aerobica", "localizada", "jump") -> ExerciseSessionRecord.EXERCISE_TYPE_EXERCISE_CLASS
                has("yoga") -> ExerciseSessionRecord.EXERCISE_TYPE_YOGA
                has("pilates") -> ExerciseSessionRecord.EXERCISE_TYPE_PILATES
                has("alongamento", "mobilidade") -> ExerciseSessionRecord.EXERCISE_TYPE_STRETCHING
                has("boxe") -> ExerciseSessionRecord.EXERCISE_TYPE_BOXING
                has("jiu", "judo", "karate", "taekwondo", "muay", "kickbox", "capoeira", "mma", "krav", "artes marciais", "tai chi") ->
                    ExerciseSessionRecord.EXERCISE_TYPE_MARTIAL_ARTS
                has("futevolei", "futebol", "futsal", "altinha") -> ExerciseSessionRecord.EXERCISE_TYPE_SOCCER
                has("volei") -> ExerciseSessionRecord.EXERCISE_TYPE_VOLLEYBALL
                has("basquete") -> ExerciseSessionRecord.EXERCISE_TYPE_BASKETBALL
                has("handebol") -> ExerciseSessionRecord.EXERCISE_TYPE_HANDBALL
                has("rugbi") -> ExerciseSessionRecord.EXERCISE_TYPE_RUGBY
                has("tenis de mesa") -> ExerciseSessionRecord.EXERCISE_TYPE_TABLE_TENNIS
                has("tenis", "padel", "frescobol") -> ExerciseSessionRecord.EXERCISE_TYPE_TENNIS
                has("badminton") -> ExerciseSessionRecord.EXERCISE_TYPE_BADMINTON
                has("squash") -> ExerciseSessionRecord.EXERCISE_TYPE_SQUASH
                has("skate", "patins") -> ExerciseSessionRecord.EXERCISE_TYPE_SKATING
                has("escalada", "boulder") -> ExerciseSessionRecord.EXERCISE_TYPE_ROCK_CLIMBING
                has("golfe") -> ExerciseSessionRecord.EXERCISE_TYPE_GOLF
                has("esqui") -> ExerciseSessionRecord.EXERCISE_TYPE_SKIING
                has("danc", "zumba", "forro", "samba", "bale", "hip hop", "axe", "jazz", "sertanejo") -> ExerciseSessionRecord.EXERCISE_TYPE_DANCING
                "danca" in c -> ExerciseSessionRecord.EXERCISE_TYPE_DANCING
                "lutas" in c -> ExerciseSessionRecord.EXERCISE_TYPE_MARTIAL_ARTS
                else -> ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT
            }
        }

        /** Nome em português de um tipo de exercício do Health Connect. */
        fun exerciseName(type: Int): String = when (type) {
            ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> "Caminhada"
            ExerciseSessionRecord.EXERCISE_TYPE_RUNNING -> "Corrida"
            ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL -> "Corrida na esteira"
            ExerciseSessionRecord.EXERCISE_TYPE_BIKING -> "Ciclismo"
            ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY -> "Bicicleta ergométrica"
            ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL -> "Natação na piscina"
            ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> "Natação em águas abertas"
            ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING -> "Musculação"
            ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING -> "Levantamento de peso"
            ExerciseSessionRecord.EXERCISE_TYPE_CALISTHENICS -> "Calistenia"
            ExerciseSessionRecord.EXERCISE_TYPE_YOGA -> "Yoga"
            ExerciseSessionRecord.EXERCISE_TYPE_PILATES -> "Pilates"
            ExerciseSessionRecord.EXERCISE_TYPE_STRETCHING -> "Alongamento"
            ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> "Trilha"
            ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL -> "Elíptico"
            ExerciseSessionRecord.EXERCISE_TYPE_ROWING -> "Remo"
            ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE -> "Remo ergômetro"
            ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING -> "Subir escadas"
            ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING_MACHINE -> "Simulador de escada"
            ExerciseSessionRecord.EXERCISE_TYPE_DANCING -> "Dança"
            ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING -> "HIIT"
            ExerciseSessionRecord.EXERCISE_TYPE_BOOT_CAMP -> "Treino funcional"
            ExerciseSessionRecord.EXERCISE_TYPE_EXERCISE_CLASS -> "Aula de ginástica"
            ExerciseSessionRecord.EXERCISE_TYPE_GYMNASTICS -> "Ginástica"
            ExerciseSessionRecord.EXERCISE_TYPE_SOCCER -> "Futebol"
            ExerciseSessionRecord.EXERCISE_TYPE_VOLLEYBALL -> "Vôlei"
            ExerciseSessionRecord.EXERCISE_TYPE_BASKETBALL -> "Basquete"
            ExerciseSessionRecord.EXERCISE_TYPE_HANDBALL -> "Handebol"
            ExerciseSessionRecord.EXERCISE_TYPE_RUGBY -> "Rúgbi"
            ExerciseSessionRecord.EXERCISE_TYPE_FOOTBALL_AMERICAN -> "Futebol americano"
            ExerciseSessionRecord.EXERCISE_TYPE_BASEBALL -> "Beisebol"
            ExerciseSessionRecord.EXERCISE_TYPE_TENNIS -> "Tênis"
            ExerciseSessionRecord.EXERCISE_TYPE_TABLE_TENNIS -> "Tênis de mesa"
            ExerciseSessionRecord.EXERCISE_TYPE_BADMINTON -> "Badminton"
            ExerciseSessionRecord.EXERCISE_TYPE_SQUASH -> "Squash"
            ExerciseSessionRecord.EXERCISE_TYPE_RACQUETBALL -> "Raquetebol"
            ExerciseSessionRecord.EXERCISE_TYPE_BOXING -> "Boxe"
            ExerciseSessionRecord.EXERCISE_TYPE_MARTIAL_ARTS -> "Artes marciais"
            ExerciseSessionRecord.EXERCISE_TYPE_FENCING -> "Esgrima"
            ExerciseSessionRecord.EXERCISE_TYPE_PADDLING -> "Canoagem / caiaque"
            ExerciseSessionRecord.EXERCISE_TYPE_SURFING -> "Surfe"
            ExerciseSessionRecord.EXERCISE_TYPE_SAILING -> "Vela"
            ExerciseSessionRecord.EXERCISE_TYPE_SCUBA_DIVING -> "Mergulho"
            ExerciseSessionRecord.EXERCISE_TYPE_WATER_POLO -> "Polo aquático"
            ExerciseSessionRecord.EXERCISE_TYPE_SKATING -> "Patinação / skate"
            ExerciseSessionRecord.EXERCISE_TYPE_ICE_SKATING -> "Patinação no gelo"
            ExerciseSessionRecord.EXERCISE_TYPE_SKIING -> "Esqui"
            ExerciseSessionRecord.EXERCISE_TYPE_SNOWBOARDING -> "Snowboard"
            ExerciseSessionRecord.EXERCISE_TYPE_ROCK_CLIMBING -> "Escalada"
            ExerciseSessionRecord.EXERCISE_TYPE_GOLF -> "Golfe"
            ExerciseSessionRecord.EXERCISE_TYPE_FRISBEE_DISC -> "Frisbee"
            ExerciseSessionRecord.EXERCISE_TYPE_GUIDED_BREATHING -> "Respiração guiada"
            ExerciseSessionRecord.EXERCISE_TYPE_WHEELCHAIR -> "Cadeira de rodas"
            ExerciseSessionRecord.EXERCISE_TYPE_PARAGLIDING -> "Parapente"
            else -> "Treino"
        }

        /** Intensidade típica (MET) por tipo, para estimar treinos que chegam sem calorias. */
        fun metForType(type: Int): Double = when (type) {
            ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> 3.5
            ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> 6.0
            ExerciseSessionRecord.EXERCISE_TYPE_RUNNING, ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL -> 9.0
            ExerciseSessionRecord.EXERCISE_TYPE_BIKING -> 7.0
            ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY -> 6.8
            ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL, ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> 6.0
            ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING, ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING -> 3.5
            ExerciseSessionRecord.EXERCISE_TYPE_CALISTHENICS -> 3.8
            ExerciseSessionRecord.EXERCISE_TYPE_YOGA, ExerciseSessionRecord.EXERCISE_TYPE_STRETCHING -> 2.5
            ExerciseSessionRecord.EXERCISE_TYPE_PILATES -> 3.0
            ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL -> 5.0
            ExerciseSessionRecord.EXERCISE_TYPE_ROWING, ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE -> 7.0
            ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING, ExerciseSessionRecord.EXERCISE_TYPE_BOOT_CAMP -> 8.0
            ExerciseSessionRecord.EXERCISE_TYPE_DANCING -> 5.0
            ExerciseSessionRecord.EXERCISE_TYPE_SOCCER -> 7.0
            ExerciseSessionRecord.EXERCISE_TYPE_TENNIS -> 7.3
            ExerciseSessionRecord.EXERCISE_TYPE_MARTIAL_ARTS, ExerciseSessionRecord.EXERCISE_TYPE_BOXING -> 7.8
            else -> 5.0
        }

        private fun normalize(text: String): String =
            Normalizer.normalize(text.trim().lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

        /** Executa uma chamada ao Health Connect sem deixar erro (sem permissão, app ausente...) quebrar o app. */
        private inline fun <T> attempt(block: () -> T): T? = try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }
}
