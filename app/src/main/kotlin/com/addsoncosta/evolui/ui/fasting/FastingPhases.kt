package com.addsoncosta.evolui.ui.fasting

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.addsoncosta.evolui.ui.components.PtBr
import com.addsoncosta.evolui.ui.components.TimeFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Fases aproximadas do jejum, por horas desde a última refeição.
 * Linguagem simples e sem promessas: cada corpo responde de um jeito.
 */
enum class FastPhase(
    val fromHours: Int,
    val toHours: Int?,
    val emoji: String,
    val title: String,
    val short: String,
    val meaning: String,
) {
    DIGESTION(
        0, 4, "🍽️", "Digestão",
        "O corpo digere a última refeição.",
        "Nas primeiras horas o corpo ainda está digerindo e usando a energia da última refeição. O açúcar e a insulina no sangue sobem e depois começam a voltar ao normal.",
    ),
    SUGAR(
        4, 8, "🩸", "Açúcar no sangue estabilizando",
        "A glicose volta ao nível de repouso.",
        "A digestão termina e o açúcar no sangue se estabiliza. O corpo passa a usar a reserva de açúcar guardada no fígado (glicogênio). É normal sentir um pouco de fome nesse período.",
    ),
    INSULIN(
        8, 12, "📉", "Queda da insulina",
        "A insulina cai e o glicogênio é usado.",
        "Com a insulina mais baixa, o corpo vai gastando o glicogênio do fígado e começa a se preparar para usar gordura como combustível.",
    ),
    FAT_START(
        12, 14, "🔥", "Começo da queima de gordura",
        "A gordura passa a ser mais usada.",
        "Com menos glicogênio disponível, o corpo aumenta o uso de gordura para gerar energia. Beba água ao longo do jejum.",
    ),
    KETOSIS(
        14, 16, "⚡", "Cetose leve",
        "O fígado começa a produzir cetonas.",
        "O fígado transforma parte da gordura em cetonas, um combustível alternativo para o corpo e o cérebro. Algumas pessoas relatam mais foco; outras, dor de cabeça leve.",
    ),
    FAT_UP(
        16, 18, "💪", "Queima de gordura intensificada",
        "Uso de gordura mais alto.",
        "A queima de gordura e a produção de cetonas aumentam. É a faixa do protocolo 16:8, o mais usado. Se sentir tontura ou mal-estar, encerre o jejum.",
    ),
    AUTOPHAGY(
        18, 24, "♻️", "Início da autofagia",
        "Começa a renovação celular.",
        "Autofagia é o processo em que as células \"fazem faxina\", reciclando partes velhas. Estudos sugerem que ela aumenta com o jejum, mas o momento exato varia de pessoa para pessoa.",
    ),
    AUTOPHAGY_UP(
        24, 48, "🧬", "Autofagia aumentada",
        "Renovação celular mais intensa.",
        "Jejuns acima de 24 horas aumentam a autofagia, mas também os riscos (queda de pressão, fraqueza, alterações de sais minerais). Não é necessário para emagrecer.",
    ),
    PROLONGED(
        48, null, "🩺", "Jejum prolongado",
        "Só com orientação médica.",
        "De 48 a 72 horas ou mais é um jejum prolongado. Ele só deve ser feito com acompanhamento médico, por causa dos riscos à saúde.",
    ),
    ;

    val range: String get() = if (toHours == null) "$fromHours h ou mais" else "$fromHours–$toHours h"

    companion object {
        fun at(hours: Double): FastPhase = entries.last { hours >= it.fromHours }
    }
}

const val FASTING_DISCLAIMER =
    "Valores aproximados; não substitui orientação médica. Gestantes, diabéticos e pessoas com histórico de transtorno alimentar devem falar com o médico."

private val WeekdayTime: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE HH:mm", PtBr)

/** Quando a fase começa no jejum atual: "22:30" (hoje) ou "qua 08:30". */
private fun startLabel(startMillis: Long, phase: FastPhase): String {
    val zone = ZoneId.systemDefault()
    val at = Instant.ofEpochMilli(startMillis + phase.fromHours * 3_600_000L).atZone(zone)
    val today = Instant.ofEpochMilli(System.currentTimeMillis()).atZone(zone).toLocalDate()
    return if (at.toLocalDate() == today) "às ${at.format(TimeFormat)}" else at.format(WeekdayTime)
}

/**
 * Linha do tempo das fases. Com um jejum em andamento ([startMillis] e [elapsedHours]),
 * a fase atual fica em destaque, as passadas marcadas e as futuras apagadas, com o horário de início.
 */
@Composable
fun PhaseTimeline(startMillis: Long?, elapsedHours: Double?) {
    var open by remember { mutableStateOf<FastPhase?>(null) }
    val current = elapsedHours?.let { FastPhase.at(it) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FastPhase.entries.forEach { phase ->
            val state = when {
                current == null -> PhaseState.INFO
                phase == current -> PhaseState.CURRENT
                phase.ordinal < current.ordinal -> PhaseState.PAST
                else -> PhaseState.FUTURE
            }
            PhaseRow(phase, state, startMillis?.let { startLabel(it, phase) }, onClick = { open = phase })
        }
    }
    open?.let { PhaseDialog(it, onDismiss = { open = null }) }
}

private enum class PhaseState { INFO, PAST, CURRENT, FUTURE }

@Composable
private fun PhaseRow(phase: FastPhase, state: PhaseState, startsAt: String?, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val shape = MaterialTheme.shapes.small
    val base = Modifier
        .fillMaxWidth()
        .alpha(if (state == PhaseState.FUTURE) 0.45f else 1f)
        .clickable(onClick = onClick)
    val decorated = if (state == PhaseState.CURRENT) {
        base.background(primary.copy(alpha = 0.12f), shape).border(1.5.dp, primary, shape)
    } else base
    Row(
        decorated.padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(44.dp)
                .background(
                    if (state == PhaseState.CURRENT) primary.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(phase.emoji, fontSize = 22.sp)
        }
        Column(Modifier.weight(1f)) {
            Text(
                phase.title,
                fontWeight = if (state == PhaseState.CURRENT) FontWeight.ExtraBold else FontWeight.Bold,
                color = if (state == PhaseState.CURRENT) primary else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                phase.range + (if (startsAt != null) " · começa $startsAt" else "") + (if (state == PhaseState.CURRENT) " · agora" else ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state == PhaseState.PAST) Icon(Icons.Filled.Check, "Fase concluída", tint = primary)
    }
}

/** Explicação de uma fase. */
@Composable
fun PhaseDialog(phase: FastPhase, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Text(phase.emoji, fontSize = 36.sp) },
        title = { Text(phase.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(phase.range, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(phase.meaning)
                Text(FASTING_DISCLAIMER, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Entendi") } },
    )
}
