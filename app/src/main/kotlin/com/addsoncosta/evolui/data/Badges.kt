package com.addsoncosta.evolui.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.ui.graphics.vector.ImageVector
import com.addsoncosta.evolui.core.GoalType
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Cor de destaque de um badge; na tela segue o tema (ver BadgesScreen), na imagem usa a cor fixa [argb]. */
enum class BadgeColor(val argb: Int) {
    NEON(0xFF39FF88.toInt()),
    CYAN(0xFF22D3EE.toInt()),
    ORANGE(0xFFFF8A3D.toInt()),
    PURPLE(0xFFB98CFF.toInt()),
    RED(0xFFFF5C6C.toInt()),
}

/** Números do usuário usados para decidir as conquistas. */
class BadgeFacts(
    val mealDays: Int,
    val bestMealStreak: Int,
    val workouts: Int,
    val bestWeekMinutes: Double,
    val fastsStarted: Int,
    val fastsOnTarget: Int,
    val longestFastHours: Double,
    val weightDays: Int,
    val goalType: GoalType?,
    /** Quanto o peso já andou na direção da meta (kg, nunca negativo). */
    val bestProgressKg: Double,
    val goalReached: Boolean,
    val waterGoalDays: Int,
    val photos: Int,
    val photoDays: Int,
    val exams: Int,
    val consults: Int,
    val anamnesis: Boolean,
    val checkup: Boolean,
)

/**
 * Uma conquista do catálogo. [progress] devolve (atual, alvo); ganha quando atual >= alvo.
 * [applies] esconde badges que não fazem sentido para o usuário (ex.: perder peso para quem quer manter).
 */
class Badge(
    val key: String,
    val name: String,
    val description: String,
    val icon: ImageVector,
    val emoji: String,
    val color: BadgeColor,
    val unit: String,
    val applies: (BadgeFacts) -> Boolean = { true },
    val progress: (BadgeFacts) -> Pair<Double, Double>,
) {
    fun earned(f: BadgeFacts): Boolean = applies(f) && progress(f).let { (cur, target) -> cur >= target }

    /** Fração concluída, de 0 a 1. */
    fun fraction(f: BadgeFacts): Float = progress(f).let { (cur, target) -> if (target <= 0) 1f else (cur / target).toFloat().coerceIn(0f, 1f) }
}

private fun count(n: Int, target: Int) = n.toDouble() to target.toDouble()
private fun yes(b: Boolean) = (if (b) 1.0 else 0.0) to 1.0
private val changesWeight: (BadgeFacts) -> Boolean = { it.goalType == GoalType.LOSE || it.goalType == GoalType.GAIN }
private fun lostOrGained(f: BadgeFacts) = if (f.goalType == GoalType.GAIN) "ganho" else "perdido"

/** Catálogo de conquistas, na ordem em que aparecem na tela. */
object Badges {
    val all: List<Badge> = listOf(
        Badge("primeira_refeicao", "Primeiro prato", "Registrou a primeira refeição.", Icons.Filled.Restaurant, "🍽️", BadgeColor.NEON, "dia") { count(it.mealDays, 1) },
        Badge("sequencia_3", "3 dias seguidos", "Registrou a alimentação 3 dias seguidos.", Icons.Filled.Whatshot, "🔥", BadgeColor.ORANGE, "dias") { count(it.bestMealStreak, 3) },
        Badge("sequencia_7", "Uma semana firme", "7 dias seguidos registrando o que comeu.", Icons.Filled.LocalFireDepartment, "🔥", BadgeColor.ORANGE, "dias") { count(it.bestMealStreak, 7) },
        Badge("sequencia_30", "Um mês de foco", "30 dias seguidos registrando a alimentação.", Icons.Filled.CalendarMonth, "📅", BadgeColor.PURPLE, "dias") { count(it.bestMealStreak, 30) },
        Badge("sequencia_100", "100 dias!", "100 dias seguidos registrando. Hábito formado!", Icons.Filled.WorkspacePremium, "🏆", BadgeColor.PURPLE, "dias") { count(it.bestMealStreak, 100) },

        Badge("primeiro_treino", "Primeiro treino", "Registrou o primeiro treino.", Icons.AutoMirrored.Filled.DirectionsRun, "🏃", BadgeColor.CYAN, "treino") { count(it.workouts, 1) },
        Badge("treinos_10", "10 treinos", "Completou 10 treinos.", Icons.Filled.FitnessCenter, "💪", BadgeColor.CYAN, "treinos") { count(it.workouts, 10) },
        Badge("treinos_50", "50 treinos", "Completou 50 treinos.", Icons.Filled.FitnessCenter, "💪", BadgeColor.CYAN, "treinos") { count(it.workouts, 50) },
        Badge("treinos_100", "100 treinos", "Completou 100 treinos. Atleta!", Icons.Filled.MilitaryTech, "🥇", BadgeColor.CYAN, "treinos") { count(it.workouts, 100) },
        Badge("semana_150", "Semana ativa", "150 minutos de treino numa mesma semana (segunda a domingo), como recomenda a OMS.", Icons.Filled.Bolt, "⚡", BadgeColor.NEON, "min") { it.bestWeekMinutes to 150.0 },

        Badge("primeiro_jejum", "Primeiro jejum", "Começou o primeiro jejum.", Icons.Filled.Timer, "⏱️", BadgeColor.PURPLE, "jejum") { count(it.fastsStarted, 1) },
        Badge("jejum_16", "16 horas", "Fez um jejum de pelo menos 16 horas.", Icons.Filled.Timer, "⏳", BadgeColor.PURPLE, "h") { it.longestFastHours to 16.0 },
        Badge("jejuns_10", "10 jejuns", "Concluiu 10 jejuns batendo a meta.", Icons.Filled.Star, "🌙", BadgeColor.PURPLE, "jejuns") { count(it.fastsOnTarget, 10) },
        Badge("jejuns_30", "30 jejuns", "Concluiu 30 jejuns batendo a meta.", Icons.Filled.Star, "🌟", BadgeColor.PURPLE, "jejuns") { count(it.fastsOnTarget, 30) },

        Badge("primeira_pesagem", "Na balança", "Registrou o peso pela primeira vez.", Icons.Filled.MonitorWeight, "⚖️", BadgeColor.NEON, "dia") { count(it.weightDays, 1) },
        Badge("kg_1", "Primeiro quilo", "O primeiro kg na direção da sua meta.", Icons.Filled.MonitorWeight, "🎯", BadgeColor.NEON, "kg", applies = changesWeight) { it.bestProgressKg to 1.0 },
        Badge("kg_5", "5 kg", "5 kg na direção da sua meta.", Icons.Filled.EmojiEvents, "🏅", BadgeColor.NEON, "kg", applies = changesWeight) { it.bestProgressKg to 5.0 },
        Badge("kg_10", "10 kg", "10 kg na direção da sua meta. Incrível!", Icons.Filled.EmojiEvents, "🏆", BadgeColor.NEON, "kg", applies = changesWeight) { it.bestProgressKg to 10.0 },
        Badge("meta_peso", "Meta alcançada", "Chegou ao peso da meta!", Icons.Filled.Flag, "🏁", BadgeColor.ORANGE, "", applies = changesWeight) { yes(it.goalReached) },

        Badge("agua_1", "Bem hidratado", "Bateu a meta de água em um dia.", Icons.Filled.WaterDrop, "💧", BadgeColor.CYAN, "dia") { count(it.waterGoalDays, 1) },
        Badge("agua_7", "7 dias de água", "Bateu a meta de água em 7 dias.", Icons.Filled.WaterDrop, "💧", BadgeColor.CYAN, "dias") { count(it.waterGoalDays, 7) },
        Badge("agua_30", "30 dias de água", "Bateu a meta de água em 30 dias.", Icons.Filled.WaterDrop, "🌊", BadgeColor.CYAN, "dias") { count(it.waterGoalDays, 30) },

        Badge("primeira_foto", "Primeira foto", "Guardou a primeira foto de progresso.", Icons.Filled.PhotoCamera, "📸", BadgeColor.ORANGE, "foto") { count(it.photos, 1) },
        Badge("antes_depois", "Antes e depois", "Tem fotos em duas datas diferentes para comparar.", Icons.Filled.Compare, "🪞", BadgeColor.ORANGE, "datas") { count(it.photoDays, 2) },

        Badge("primeiro_exame", "Exame em dia", "Registrou o primeiro exame.", Icons.Filled.Science, "🧪", BadgeColor.RED, "exame") { count(it.exams, 1) },
        Badge("primeira_consulta", "Consulta registrada", "Registrou a primeira consulta.", Icons.Filled.MedicalServices, "🩺", BadgeColor.RED, "consulta") { count(it.consults, 1) },
        Badge("anamnese", "Histórico de saúde", "Preencheu a anamnese (doenças e histórico).", Icons.Filled.Description, "📋", BadgeColor.RED, "") { yes(it.anamnesis) },
        Badge("checkup", "Check-up do ano", "Consulta e exame registrados nos últimos 12 meses.", Icons.Filled.HealthAndSafety, "❤️", BadgeColor.RED, "") { yes(it.checkup) },
        Badge("colecionador", "Colecionador", "Ganhou 10 conquistas.", Icons.Filled.Verified, "🎖️", BadgeColor.PURPLE, "conquistas") { 0.0 to 10.0 },
    )

    private val byKey = all.associateBy { it.key }

    fun byKey(key: String): Badge? = byKey[key]

    /** Celebração genérica para o aviso. */
    val celebrationIcon: ImageVector get() = Icons.Filled.Celebration
}

/** Situação de um badge para a tela: ganho (com data) ou o quanto falta. */
data class BadgeStatus(val badge: Badge, val earned: BadgeEntity?, val current: Double, val target: Double, val fraction: Float)

/** Lê os dados do banco e calcula os números das conquistas. */
suspend fun badgeFacts(db: AppDatabase, settings: AppSettings, today: LocalDate = LocalDate.now()): BadgeFacts = withContext(Dispatchers.IO) {
    val profile = db.profileDao().get()?.toProfile()
    val mealDays = db.mealDao().all().map { it.epochDay }.toSortedSet()
    val workouts = db.workoutDao().all()
    val fasts = db.fastDao().all()
    val logs = db.dayLogDao().all().sortedBy { it.epochDay }
    val photos = db.photoDao().all()
    val records = db.healthRecordDao().all()
    val conditions = db.healthConditionDao().all()

    // Maior sequência de dias seguidos com refeição registrada.
    var best = 0
    var run = 0
    var prev: Long? = null
    for (d in mealDays) {
        run = if (prev != null && d == prev + 1) run + 1 else 1
        best = max(best, run)
        prev = d
    }

    val weekMinutes = workouts
        .groupBy { LocalDate.ofEpochDay(it.epochDay).with(DayOfWeek.MONDAY) }
        .values.maxOfOrNull { list -> list.sumOf { it.minutes } } ?: 0.0

    val ended = fasts.filter { it.endMillis != null }
    val onTarget = ended.count { it.endMillis!! - it.startMillis >= it.targetHours * 3_600_000L }
    val longest = ended.maxOfOrNull { (it.endMillis!! - it.startMillis) / 3_600_000.0 } ?: 0.0

    val weights = logs.mapNotNull { l -> l.weightKg?.let { l.epochDay to it } }
    val start = profile?.startWeightKg
    val goal = profile?.goalType
    val progressKg = if (start == null || weights.isEmpty()) 0.0 else when (goal) {
        GoalType.LOSE -> (start - weights.minOf { it.second }).coerceAtLeast(0.0)
        GoalType.GAIN -> (weights.maxOf { it.second } - start).coerceAtLeast(0.0)
        else -> 0.0
    }
    val latest = weights.lastOrNull()?.second
    val goalReached = profile != null && latest != null && when (goal) {
        GoalType.LOSE -> latest <= profile.targetWeightKg + 0.05
        GoalType.GAIN -> latest >= profile.targetWeightKg - 0.05
        else -> false
    }

    // Água: meta do dia pelo peso daquele dia (ou o inicial), ou a escolhida pelo usuário.
    val override = settings.waterGoalMl.value
    var weightSoFar = start ?: 70.0
    var waterDays = 0
    for (l in logs) {
        l.weightKg?.let { weightSoFar = it }
        val ml = l.waterMl ?: continue
        if (ml > 0 && ml >= waterGoalMl(weightSoFar, override)) waterDays++
    }

    val yearAgo = today.minusDays(365).toEpochDay()
    val exams = records.filter { it.type == HealthType.EXAME.name }
    val consults = records.filter { it.type == HealthType.CONSULTA.name }
    val anamnesis = conditions.isNotEmpty() || records.any { it.type == HealthType.ANAMNESE.name }

    BadgeFacts(
        mealDays = mealDays.size,
        bestMealStreak = best,
        workouts = workouts.size,
        bestWeekMinutes = weekMinutes,
        fastsStarted = fasts.size,
        fastsOnTarget = onTarget,
        longestFastHours = longest,
        weightDays = weights.size,
        goalType = goal,
        bestProgressKg = progressKg,
        goalReached = goalReached,
        waterGoalDays = waterDays,
        photos = photos.size,
        photoDays = photos.map { it.epochDay }.distinct().size,
        exams = exams.size,
        consults = consults.size,
        anamnesis = anamnesis,
        checkup = exams.any { it.epochDay >= yearAgo } && consults.any { it.epochDay >= yearAgo },
    )
}

/** Situação de todas as conquistas (para a tela), já contando as ganhas antes. */
suspend fun badgeStatuses(db: AppDatabase, settings: AppSettings): List<BadgeStatus> = withContext(Dispatchers.IO) {
    val facts = badgeFacts(db, settings)
    val earned = db.badgeDao().all().associateBy { it.key }
    Badges.all.filter { it.applies(facts) || it.key in earned }.map { b ->
        val (cur, target) = if (b.key == COLLECTOR) earned.size.toDouble() to 10.0 else b.progress(facts)
        val fraction = if (b.key in earned) 1f else if (target <= 0) 0f else (cur / target).toFloat().coerceIn(0f, 1f)
        BadgeStatus(b, earned[b.key], cur, target, fraction)
    }
}

private const val COLLECTOR = "colecionador"

/**
 * Calcula as conquistas, grava as novas (sem repetir) e devolve só as recém-ganhas.
 * Pode ser chamada à vontade: roda fora da thread principal.
 */
suspend fun checkBadges(db: AppDatabase, settings: AppSettings): List<BadgeEntity> = withContext(Dispatchers.IO) {
    val facts = badgeFacts(db, settings)
    val known = db.badgeDao().all().map { it.key }.toSet()
    val now = System.currentTimeMillis()
    val fresh = Badges.all
        .filter { it.key != COLLECTOR && it.key !in known && it.earned(facts) }
        .map { BadgeEntity(it.key, now) }
        .toMutableList()
    if (COLLECTOR !in known && known.size + fresh.size >= 10) fresh += BadgeEntity(COLLECTOR, now)
    if (fresh.isNotEmpty()) db.badgeDao().insertAll(fresh)
    fresh
}

/** Data e hora em que o badge foi ganho. */
fun BadgeEntity.earnedDate(): LocalDate = Instant.ofEpochMilli(earnedMillis).atZone(ZoneId.systemDefault()).toLocalDate()
