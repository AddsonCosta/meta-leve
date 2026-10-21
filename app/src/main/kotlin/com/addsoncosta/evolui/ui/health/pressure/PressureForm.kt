package com.addsoncosta.evolui.ui.health.pressure

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.addsoncosta.evolui.data.BloodPressureEntity
import com.addsoncosta.evolui.health.BpCategory
import com.addsoncosta.evolui.health.PressureGuess
import com.addsoncosta.evolui.ui.components.NumberField
import com.addsoncosta.evolui.ui.components.Overline
import com.addsoncosta.evolui.ui.health.ConfirmDialog
import com.addsoncosta.evolui.ui.health.DateField
import com.addsoncosta.evolui.ui.health.HelpField
import com.addsoncosta.evolui.ui.health.care.FormDialog
import com.addsoncosta.evolui.ui.health.care.TimePick
import com.addsoncosta.evolui.ui.health.care.minuteText
import com.addsoncosta.evolui.ui.theme.Evolui
import java.time.LocalDate

/** Faixas aceitas no formulário (fora disso, quase certamente é erro de digitação ou de leitura). */
private val SYS_OK = 50..300
private val DIA_OK = 30..200
private val PULSE_OK = 20..250

/**
 * Formulário de uma medida. Quando veio de foto, mostra o aviso para conferir e, se a leitura
 * achou mais de uma possibilidade, deixa escolher. Tudo continua editável.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PressureForm(
    initial: BloodPressureEntity,
    fromPhoto: Boolean,
    guesses: List<PressureGuess>,
    ocrText: String?,
    onDismiss: () -> Unit,
    onSave: (BloodPressureEntity) -> Unit,
    onDelete: (BloodPressureEntity) -> Unit,
) {
    val start = remember(initial) { dateTimeOf(initial.epochMillis) }
    var sys by remember(initial) { mutableStateOf(initial.systolic.takeIf { it > 0 }?.toString() ?: "") }
    var dia by remember(initial) { mutableStateOf(initial.diastolic.takeIf { it > 0 }?.toString() ?: "") }
    var pulse by remember(initial) { mutableStateOf(initial.pulse?.toString() ?: "") }
    var day by remember(initial) { mutableStateOf(start.toLocalDate().toEpochDay()) }
    var minute by remember(initial) { mutableStateOf(start.hour * 60 + start.minute) }
    var arm by remember(initial) { mutableStateOf(bpArmOf(initial.arm)) }
    var position by remember(initial) { mutableStateOf(bpPositionOf(initial.position)) }
    var notes by remember(initial) { mutableStateOf(initial.notes ?: "") }
    var pickTime by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showText by remember { mutableStateOf(false) }

    val s = sys.toIntOrNull()
    val d = dia.toIntOrNull()
    val p = pulse.toIntOrNull()
    val sysError = when {
        sys.isEmpty() -> null
        s == null || s !in SYS_OK -> "Entre ${SYS_OK.first} e ${SYS_OK.last}"
        else -> null
    }
    val diaError = when {
        dia.isEmpty() -> null
        d == null || d !in DIA_OK -> "Entre ${DIA_OK.first} e ${DIA_OK.last}"
        d != null && s != null && d >= s -> "Deve ser menor que a sistólica"
        else -> null
    }
    val pulseError = if (pulse.isNotEmpty() && (p == null || p !in PULSE_OK)) "Entre ${PULSE_OK.first} e ${PULSE_OK.last}" else null
    val valid = s != null && d != null && sysError == null && diaError == null && pulseError == null
    val today = LocalDate.now().toEpochDay()

    fun build() = initial.copy(
        epochMillis = millisOf(day, minute),
        systolic = s ?: 0,
        diastolic = d ?: 0,
        pulse = p,
        arm = arm?.name,
        position = position?.name,
        notes = notes.trim().ifBlank { null },
    )

    FormDialog(
        title = if (initial.id == 0L) "Nova medida" else "Editar medida",
        onDismiss = onDismiss,
        onSave = { if (valid) onSave(build()) },
        saveEnabled = valid,
    ) {
        if (fromPhoto) {
            Row(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(Evolui.Orange.copy(alpha = 0.16f)),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(Icons.Filled.Warning, null, tint = Evolui.Orange, modifier = Modifier.padding(12.dp))
                Column(Modifier.weight(1f).padding(top = 12.dp, end = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Confira os valores", fontWeight = FontWeight.Bold)
                    Text(
                        if (guesses.isEmpty()) "Não encontramos os números na foto. Digite olhando o visor do aparelho."
                        else "A leitura automática erra com frequência nos números do visor. Compare com o aparelho e corrija antes de salvar.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (guesses.size > 1) {
                Overline("Encontramos mais de uma leitura")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    guesses.take(6).forEach { g ->
                        val on = sys == g.systolic.toString() && dia == g.diastolic.toString() && pulse == (g.pulse?.toString() ?: "")
                        FilterChip(
                            selected = on,
                            onClick = {
                                sys = g.systolic.toString()
                                dia = g.diastolic.toString()
                                pulse = g.pulse?.toString() ?: ""
                            },
                            label = { Text("${g.systolic}/${g.diastolic}" + (g.pulse?.let { " · $it" } ?: "")) },
                        )
                    }
                }
            }
            if (!ocrText.isNullOrBlank()) {
                TextButton(onClick = { showText = !showText }) { Text(if (showText) "Esconder o texto lido" else "Ver o texto lido na foto") }
                if (showText) {
                    Text(ocrText.take(600), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumberField(sys, { sys = it.take(3) }, "Sistólica", Modifier.weight(1f), decimal = false, help = "máxima", error = sysError, highlight = fromPhoto)
            NumberField(dia, { dia = it.take(3) }, "Diastólica", Modifier.weight(1f), decimal = false, help = "mínima", error = diaError, highlight = fromPhoto)
        }
        NumberField(pulse, { pulse = it.take(3) }, "Pulso (opcional)", Modifier.fillMaxWidth(), suffix = "bpm", decimal = false, error = pulseError, highlight = fromPhoto)

        if (s != null && d != null && valid) {
            val cat = BpCategory.of(s, d)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(14.dp).clip(CircleShape).background(cat.color()))
                Text(cat.label, color = cat.color(), fontWeight = FontWeight.Bold)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
            DateField("Data", day, { it?.let { v -> day = minOf(v, today) } }, null, Modifier.weight(1f))
            OutlinedButton(onClick = { pickTime = true }, modifier = Modifier.weight(1f).padding(top = 8.dp)) {
                Icon(Icons.Filled.Schedule, null, Modifier.size(18.dp))
                Text("  ${minuteText(minute)}")
            }
        }

        Overline("Braço")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BpArm.entries.forEach { a -> FilterChip(selected = arm == a, onClick = { arm = if (arm == a) null else a }, label = { Text(a.label) }) }
        }
        Overline("Posição")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BpPosition.entries.forEach { o -> FilterChip(selected = position == o, onClick = { position = if (position == o) null else o }, label = { Text(o.label) }) }
        }
        HelpField(notes, { notes = it }, "Anotações", "Ex.: após café, com dor de cabeça, esqueci o remédio (opcional)", multiline = true)

        Text(BP_DISCLAIMER, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        if (initial.id != 0L) {
            TextButton(onClick = { confirmDelete = true }) {
                Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error)
                Text("  Apagar esta medida", color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (pickTime) TimePick(minute, { pickTime = false }) { minute = it }
    if (confirmDelete) {
        ConfirmDialog(
            "Apagar medida?",
            "A medida ${initial.pairText()} de ${whenText(initial.epochMillis)} será apagada.",
            "Apagar",
            onConfirm = { onDelete(initial) },
            onDismiss = { confirmDelete = false },
        )
    }
}
