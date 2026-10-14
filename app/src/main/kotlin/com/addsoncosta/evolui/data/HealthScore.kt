package com.addsoncosta.evolui.data

import com.addsoncosta.evolui.core.ActivityLevel
import com.addsoncosta.evolui.core.EnergyCalculator
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Uma parte da nota. [value] vai de 0 a 1; nulo = sem dado suficiente (o peso dela é
 * redistribuído entre as outras, para não penalizar). [weight] é o peso na nota final.
 */
data class ScoreComponent(
    val key: String,
    val label: String,
    val weight: Int,
    val value: Float?,
    val detail: String,
    val tip: String,
)

/** Nota de autocuidado (0 a 100) dos últimos 7 dias. */
data class HealthScore(
    val score: Int,
    val label: String,
    val components: List<ScoreComponent>,
    val tips: List<String>,
) {
    /** Peso efetivo de cada parte, em porcentagem, depois de redistribuir as que não têm dado. */
    fun share(c: ScoreComponent): Int {
        val total = components.filter { it.value != null }.sumOf { it.weight }
        return if (c.value == null || total == 0) 0 else (c.weight * 100.0 / total).roundToInt()
    }
}

fun scoreLabel(score: Int, hasData: Boolean): String = when {
    !hasData -> "Vamos começar"
    score >= 85 -> "Excelente"
    score >= 70 -> "Muito bom"
    score >= 50 -> "Bom"
    score >= 25 -> "Pode melhorar"
    else -> "Vamos começar"
}

private const val HOUR = 3_600_000L

/** Calcula a nota com os dados do banco (fora da thread principal). */
suspend fun computeHealthScore(db: AppDatabase, settings: AppSettings, today: LocalDate = LocalDate.now()): HealthScore =
    withContext(Dispatchers.IO) {
        val profile = db.profileDao().get()?.toProfile()
        val days = (6 downTo 0).map { today.minusDays(it.toLong()) }
        val range = days.first().toEpochDay()..today.toEpochDay()
        val meals = db.mealDao().all().filter { it.epochDay in range }
        val workouts = db.workoutDao().all().filter { it.epochDay in range }
        val logs = db.dayLogDao().all().sortedBy { it.epochDay }
        val logByDay = logs.associateBy { it.epochDay }
        val fasts = db.fastDao().all()
        val records = db.healthRecordDao().all()
        val zone = ZoneId.systemDefault()

        fun weightOn(day: Long): Double =
            logs.lastOrNull { it.epochDay <= day && it.weightKg != null }?.weightKg ?: profile?.startWeightKg ?: 70.0

        val eatenByDay = meals.groupBy { it.epochDay }.mapValues { (_, list) -> list.sumOf { it.kcal } }
        val exerciseByDay = workouts.groupBy { it.epochDay }.mapValues { (_, list) -> list.sumOf { it.kcal } }
        val components = mutableListOf<ScoreComponent>()

        // 1. Alimentação registrada.
        val loggedDays = eatenByDay.keys.size
        components += ScoreComponent(
            "registro", "Alimentação registrada", 20, loggedDays / 7f,
            "$loggedDays de 7 dias com refeições anotadas",
            "Anote o que comer todos os dias, mesmo os lanches: é o que mais ajuda a acertar a meta.",
        )

        // 2. Dentro da meta de calorias (entre 85% e 105% da meta do dia), como na tela Hoje.
        val calorieDays = if (profile == null) emptyList() else eatenByDay.entries
            .filter { (day, eaten) -> day != today.toEpochDay() || eaten > 0 }
            .map { (day, eaten) ->
                val log = logByDay[day]
                val activity = log?.activity?.let { a -> runCatching { ActivityLevel.valueOf(a) }.getOrNull() } ?: profile.defaultActivity
                val s = EnergyCalculator.summary(profile, LocalDate.ofEpochDay(day), weightOn(day), activity, eaten, exerciseByDay[day] ?: 0.0)
                val goal = s.intakeGoal
                // Hoje ainda não acabou: só conta se já chegou perto da meta.
                if (day == today.toEpochDay() && eaten < goal * 0.85) null else (goal > 0 && eaten >= goal * 0.85 && eaten <= goal * 1.05)
            }
            .filterNotNull()
        val inRange = calorieDays.count { it }
        components += ScoreComponent(
            "meta", "Dentro da meta de calorias", 20,
            if (calorieDays.isEmpty()) null else inRange.toFloat() / calorieDays.size,
            if (calorieDays.isEmpty()) "Sem dias completos registrados ainda" else "$inRange de ${calorieDays.size} dias perto da meta (85% a 105%)",
            "Tente ficar perto da meta de calorias: nem muito abaixo, nem acima. Veja quanto falta na tela Hoje.",
        )

        // 3. Treino: minutos na semana contra 150 (recomendação da OMS).
        val minutes = workouts.sumOf { it.minutes }
        components += ScoreComponent(
            "treino", "Treino", 20, (minutes / 150.0).toFloat().coerceIn(0f, 1f),
            "${minutes.roundToInt()} de 150 minutos na semana",
            "Some 150 minutos de atividade na semana; uma caminhada de 30 minutos em 5 dias já resolve.",
        )

        // 4. Água: só conta para quem anota água (algum registro nos últimos 30 dias).
        val override = settings.waterGoalMl.value
        val usesWater = logs.any { it.epochDay >= today.minusDays(30).toEpochDay() && (it.waterMl ?: 0.0) > 0 }
        val waterDays = days.count { d ->
            val ml = logByDay[d.toEpochDay()]?.waterMl ?: 0.0
            ml > 0 && ml >= waterGoalMl(weightOn(d.toEpochDay()), override)
        }
        components += ScoreComponent(
            "agua", "Água", 15, if (usesWater) waterDays / 7f else null,
            if (usesWater) "$waterDays de 7 dias com a meta de água batida" else "Você ainda não anota a água",
            "Beba água ao longo do dia e anote na tela Hoje; deixe uma garrafa por perto.",
        )

        // 5. Peso registrado na semana.
        val weighed = logs.any { it.epochDay in range && it.weightKg != null }
        components += ScoreComponent(
            "peso", "Peso registrado", 10, if (weighed) 1f else 0f,
            if (weighed) "Pesou-se nesta semana" else "Nenhuma pesagem nesta semana",
            "Suba na balança uma vez por semana, de manhã, em jejum, e anote o peso.",
        )

        // 6. Jejum: opcional, só para quem usa (algum jejum nos últimos 30 dias).
        val monthAgo = System.currentTimeMillis() - 30 * 24 * HOUR
        val usesFasting = fasts.any { it.startMillis >= monthAgo }
        val weekFasts = fasts.filter { f ->
            f.endMillis != null && Instant.ofEpochMilli(f.endMillis).atZone(zone).toLocalDate().toEpochDay() in range
        }
        val fastsOk = weekFasts.count { it.endMillis!! - it.startMillis >= it.targetHours * HOUR }
        components += ScoreComponent(
            "jejum", "Jejum", 5, if (usesFasting) (fastsOk / 2f).coerceAtMost(1f) else null,
            if (usesFasting) "$fastsOk ${if (fastsOk == 1) "jejum concluído" else "jejuns concluídos"} na semana (2 já valem nota cheia)" else "Você não usa jejum (não conta na nota)",
            "Se você faz jejum, tente concluir pelo menos 2 na semana dentro da meta escolhida.",
        )

        // 7. Cuidados em dia: consulta e exame nos últimos 12 meses.
        val yearAgo = today.minusDays(365).toEpochDay()
        val consult = records.any { it.type == HealthType.CONSULTA.name && it.epochDay >= yearAgo }
        val exam = records.any { it.type == HealthType.EXAME.name && it.epochDay >= yearAgo }
        components += ScoreComponent(
            "cuidados", "Cuidados em dia", 10, (if (consult) 0.5f else 0f) + (if (exam) 0.5f else 0f),
            when {
                consult && exam -> "Consulta e exame no último ano"
                consult -> "Consulta no último ano; falta um exame"
                exam -> "Exame no último ano; falta uma consulta"
                else -> "Nenhuma consulta ou exame registrado no último ano"
            },
            "Faça um check-up por ano: registre a consulta e os exames na aba Saúde.",
        )

        val counted = components.filter { it.value != null }
        val totalWeight = counted.sumOf { it.weight }
        val score = if (totalWeight == 0) 0 else (counted.sumOf { it.weight * it.value!!.toDouble() } / totalWeight * 100).roundToInt().coerceIn(0, 100)
        val hasData = loggedDays > 0 || workouts.isNotEmpty() || weighed
        val tips = counted
            .filter { it.value!! < 0.9f }
            .sortedByDescending { (1f - it.value!!) * it.weight }
            .take(3)
            .map { it.tip }
            .ifEmpty { listOf("Você está cuidando muito bem de si. Continue assim!") }
        HealthScore(score, scoreLabel(score, hasData), components, tips)
    }

/** Nota sempre atualizada: recalcula quando refeições, treinos, dias, jejuns ou registros de saúde mudam. */
fun healthScoreFlow(db: AppDatabase, settings: AppSettings): Flow<HealthScore> {
    val today = LocalDate.now().toEpochDay()
    val triggers: List<Flow<Unit>> = listOf(
        db.mealDao().observeDailyTotals().map { },
        db.workoutDao().observeDailyTotals().map { },
        db.dayLogDao().observeWeights().map { },
        db.dayLogDao().observe(today).map { },
        db.fastDao().observeAll().map { },
        db.healthRecordDao().observeAll().map { },
        settings.waterGoalMl.map { },
    )
    return combine(triggers) { }
        .conflate()
        .map { computeHealthScore(db, settings) }
        .flowOn(Dispatchers.IO)
}
