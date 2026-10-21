package com.addsoncosta.evolui.ui.health

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.addsoncosta.evolui.health.MedicationSchedule
import com.addsoncosta.evolui.health.PrescribedMedicine
import com.addsoncosta.evolui.health.Prescription
import com.addsoncosta.evolui.health.TakeReminders
import com.addsoncosta.evolui.ui.components.DateFormat
import com.addsoncosta.evolui.ui.components.NumberField

/** "dose · como tomar · duração" de um remédio da receita. */
fun medicineSummary(m: PrescribedMedicine): String =
    listOfNotNull(m.dose, m.posology, m.duration).joinToString(" · ").ifBlank { "Sem dose lida" }

/**
 * "Remédios da receita": o usuário escolhe quais viram registros de Medicação e se quer lembretes.
 * [note] explica o que acontece (ex.: "serão salvos junto com a consulta").
 */
@Composable
fun PrescriptionDialog(
    prescription: Prescription,
    note: String,
    confirmLabel: String,
    onConfirm: (medicines: List<PrescribedMedicine>, reminders: Boolean) -> Unit,
    onDismiss: () -> Unit,
    askNotifications: () -> Unit,
    preselected: (PrescribedMedicine) -> Boolean = { true },
) {
    val chosen = remember { mutableStateMapOf<Int, Boolean>().apply { prescription.medicines.forEachIndexed { i, m -> put(i, preselected(m)) } } }
    val anyRegular = prescription.medicines.any { !it.asNeeded }
    var reminders by remember { mutableStateOf(anyRegular) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remédios da receita") },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val head = listOfNotNull(
                    prescription.doctorWithCrm,
                    prescription.specialty,
                    prescription.date?.let { "Receita de ${it.format(DateFormat)}" },
                ).joinToString(" · ")
                if (head.isNotBlank()) Text(head, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                Text(note, style = MaterialTheme.typography.bodyMedium)
                prescription.medicines.forEachIndexed { i, m ->
                    Row(Modifier.fillMaxWidth().clickable { chosen[i] = chosen[i] != true }, verticalAlignment = Alignment.Top) {
                        Checkbox(checked = chosen[i] == true, onCheckedChange = { chosen[i] = it })
                        Column(Modifier.weight(1f)) {
                            Text(m.name, fontWeight = FontWeight.Bold)
                            Text(medicineSummary(m), style = MaterialTheme.typography.bodyMedium)
                            if (m.asNeeded) {
                                Text("Só se precisar: fica sem lembrete.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Lembrar de tomar", fontWeight = FontWeight.Bold)
                        Text(
                            "Cria avisos no calendário nos horários da receita (ex.: de 8 em 8 horas a partir das 8h), pelos dias indicados. Uso contínuo: os próximos 30 dias.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = reminders, onCheckedChange = { reminders = it; if (it) askNotifications() })
                }
                Text(
                    "Confira com a receita. A leitura automática pode errar nomes e doses.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (reminders) askNotifications()
                    onConfirm(prescription.medicines.filterIndexed { i, _ -> chosen[i] == true }, reminders)
                    onDismiss()
                },
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Agora não") } },
    )
}

/** Um campo achado no documento, para o usuário escolher se usa (tela de detalhe). */
data class FoundField(val key: String, val label: String, val value: String, val shown: String = value)

@Composable
fun FillSuggestionsDialog(fields: List<FoundField>, onApply: (List<FoundField>) -> Unit, onDismiss: () -> Unit) {
    val chosen = remember { mutableStateMapOf<String, Boolean>().apply { fields.forEach { put(it.key, true) } } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Encontramos estes dados no arquivo") },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Só aparecem os campos que estão vazios no registro. Confira antes de usar.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                fields.forEach { f ->
                    Row(Modifier.fillMaxWidth().clickable { chosen[f.key] = chosen[f.key] != true }, verticalAlignment = Alignment.Top) {
                        Checkbox(checked = chosen[f.key] == true, onCheckedChange = { chosen[f.key] = it })
                        Column(Modifier.weight(1f)) {
                            Text(f.label, fontWeight = FontWeight.Bold)
                            Text(f.shown, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(fields.filter { chosen[it.key] == true }); onDismiss() }) { Text("Usar selecionados") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Agora não") } },
    )
}

/** Lembretes para tomar um remédio ou suplemento já salvo: horários e por quantos dias. */
@Composable
fun TakeReminderDialog(
    name: String,
    initial: TakeReminders.Plan,
    onConfirm: (TakeReminders.Plan) -> Unit,
    onDismiss: () -> Unit,
    askNotifications: () -> Unit,
) {
    var timesText by remember { mutableStateOf(MedicationSchedule.format(initial.times)) }
    var daysText by remember { mutableStateOf(initial.days.toString()) }
    val times = MedicationSchedule.parseTimes(timesText)
    val days = daysText.toIntOrNull() ?: 0
    val valid = times.isNotEmpty() && days in 1..MedicationSchedule.MAX_DAYS
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Lembrar de tomar") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(name, fontWeight = FontWeight.Bold)
                HelpField(
                    timesText, { timesText = it }, "Horários",
                    "Separados por vírgula. Ex.: 08:00, 20:00",
                    error = if (times.isEmpty()) "Digite pelo menos um horário, como 08:00." else null,
                )
                NumberField(
                    value = daysText,
                    onValueChange = { daysText = it },
                    label = "Por quantos dias",
                    suffix = "dias",
                    decimal = false,
                    help = "De 1 a ${MedicationSchedule.MAX_DAYS}. Depois dá para criar mais por aqui.",
                    error = if (days !in 1..MedicationSchedule.MAX_DAYS) "Digite de 1 a ${MedicationSchedule.MAX_DAYS} dias." else null,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Os avisos aparecem no calendário de saúde e podem chegar alguns minutos depois do horário.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                askNotifications()
                onConfirm(initial.copy(times = times, days = days))
                onDismiss()
            }) { Text("Criar lembretes") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
