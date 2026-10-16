package com.addsoncosta.evolui.ui.health.pressure

import androidx.compose.ui.graphics.Color
import com.addsoncosta.evolui.data.BloodPressureEntity
import com.addsoncosta.evolui.health.BpCategory
import com.addsoncosta.evolui.ui.components.DateFormat
import com.addsoncosta.evolui.ui.components.TimeFormat
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.roundToInt

// Peças comuns da pressão arterial: rótulos, cores da classificação, médias e texto para compartilhar.

/** Braço da medida. O [name] é gravado em BloodPressureEntity.arm; não renomear. */
internal enum class BpArm(val label: String) { ESQUERDO("Braço esquerdo"), DIREITO("Braço direito") }

/** Posição na medida. O [name] é gravado em BloodPressureEntity.position; não renomear. */
internal enum class BpPosition(val label: String) { SENTADO("Sentado"), DEITADO("Deitado"), EM_PE("Em pé") }

internal fun bpArmOf(name: String?): BpArm? = BpArm.entries.firstOrNull { it.name == name }
internal fun bpPositionOf(name: String?): BpPosition? = BpPosition.entries.firstOrNull { it.name == name }

/** Cores fixas (não mudam com o tema): do verde ao vermelho escuro, como nas tabelas da diretriz. */
internal fun BpCategory.color(): Color = when (this) {
    BpCategory.OTIMA -> Color(0xFF22C55E)
    BpCategory.NORMAL -> Color(0xFF84CC16)
    BpCategory.PRE_HIPERTENSAO -> Color(0xFFEAB308)
    BpCategory.ESTAGIO_1 -> Color(0xFFF97316)
    BpCategory.ESTAGIO_2 -> Color(0xFFEF4444)
    BpCategory.ESTAGIO_3 -> Color(0xFFB91C1C)
}

internal val BloodPressureEntity.category: BpCategory get() = BpCategory.of(systolic, diastolic)

internal const val BP_DISCLAIMER =
    "Valores informativos, pela classificação da Diretriz Brasileira de Hipertensão (2020). Uma medida isolada não dá diagnóstico: consulte seu médico."

internal fun dateTimeOf(millis: Long): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault())

internal fun millisOf(epochDay: Long, minuteOfDay: Int): Long =
    java.time.LocalDate.ofEpochDay(epochDay).atTime(minuteOfDay / 60, minuteOfDay % 60)
        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

/** "16/10/2026 08:30". */
internal fun whenText(millis: Long): String {
    val dt = dateTimeOf(millis)
    return dt.toLocalDate().format(DateFormat) + " " + dt.toLocalTime().format(TimeFormat)
}

/** "128/82". */
internal fun BloodPressureEntity.pairText(): String = "$systolic/$diastolic"

/** "Braço esquerdo · sentado". */
internal fun BloodPressureEntity.detailText(): String? =
    listOfNotNull(bpArmOf(arm)?.label, bpPositionOf(position)?.label?.lowercase())
        .joinToString(" · ").ifBlank { null }

/** Média das medidas de um período. */
internal data class BpAverage(val systolic: Int, val diastolic: Int, val pulse: Int?, val count: Int) {
    val category: BpCategory get() = BpCategory.of(systolic, diastolic)
}

internal fun averageOf(list: List<BloodPressureEntity>): BpAverage? {
    if (list.isEmpty()) return null
    val pulses = list.mapNotNull { it.pulse }
    return BpAverage(
        systolic = list.map { it.systolic }.average().roundToInt(),
        diastolic = list.map { it.diastolic }.average().roundToInt(),
        pulse = if (pulses.isEmpty()) null else pulses.average().roundToInt(),
        count = list.size,
    )
}

internal const val DAY_MILLIS = 86_400_000L

/** Medidas dos últimos [days] dias (contando de agora). */
internal fun List<BloodPressureEntity>.lastDays(days: Int, now: Long = System.currentTimeMillis()): List<BloodPressureEntity> =
    filter { it.epochMillis >= now - days * DAY_MILLIS && it.epochMillis <= now + DAY_MILLIS }

/** Texto para WhatsApp ou para colar: médias, últimas medidas e o aviso. [readings] do mais recente ao mais antigo. */
internal fun pressureShareText(readings: List<BloodPressureEntity>): String = buildString {
    appendLine("Pressão arterial (Evolui)")
    if (readings.isEmpty()) {
        appendLine("Nenhuma medida registrada.")
        append(BP_DISCLAIMER)
        return@buildString
    }
    listOf(7, 30).forEach { days ->
        averageOf(readings.lastDays(days))?.let { a ->
            append("Média dos últimos $days dias: ${a.systolic}/${a.diastolic} mmHg")
            a.pulse?.let { append(", pulso $it bpm") }
            appendLine(" · ${a.category.label} (${a.count} ${if (a.count == 1) "medida" else "medidas"})")
        }
    }
    appendLine()
    appendLine("Medidas:")
    readings.take(30).forEach { r ->
        append("${whenText(r.epochMillis)}: ${r.pairText()} mmHg")
        r.pulse?.let { append(", pulso $it") }
        r.detailText()?.let { append(" ($it)") }
        append(" · ${r.category.short}")
        r.notes?.takeIf { it.isNotBlank() }?.let { append(" · $it") }
        appendLine()
    }
    if (readings.size > 30) appendLine("... e mais ${readings.size - 30} medidas anteriores.")
    appendLine()
    append(BP_DISCLAIMER)
}

/** Cor do cartão no hub da Saúde: a da classificação da última medida (nulo = sem medidas). */
internal fun pressureHubColor(latest: BloodPressureEntity?): Color? = latest?.category?.color()

/** Texto do cartão no hub da Saúde. */
internal fun pressureHubSubtitle(latest: BloodPressureEntity?): String =
    if (latest == null) "Registre e acompanhe suas medidas, até por foto"
    else "Última: ${latest.pairText()} · ${latest.category.short} · ${dateTimeOf(latest.epochMillis).toLocalDate().format(DateFormat)}"
