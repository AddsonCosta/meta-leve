package com.addsoncosta.evolui.ui.health.care

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material.icons.filled.LocalPharmacy
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.addsoncosta.evolui.data.HealthType
import com.addsoncosta.evolui.health.ReportArea
import com.addsoncosta.evolui.ui.components.Overline
import com.addsoncosta.evolui.ui.components.PtBr
import com.addsoncosta.evolui.ui.health.HealthShareButton
import com.addsoncosta.evolui.ui.theme.Evolui
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

// Peças comuns das telas de cuidado (calendário, anamnese, odontologia e ciclo).

internal val MonthTitleFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM 'de' yyyy", PtBr)
internal val LongDayFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", PtBr)

/** Tipos que podem ser marcados no calendário (bioimpedância entra como um exame à parte). */
internal val CalendarTypes: List<HealthType> = HealthType.entries.filter { it != HealthType.ANAMNESE }

internal fun healthTypeOf(name: String): HealthType = HealthType.entries.firstOrNull { it.name == name } ?: HealthType.CONSULTA

internal fun HealthType.icon(): ImageVector = when (this) {
    HealthType.CONSULTA -> Icons.Filled.MedicalServices
    HealthType.EXAME -> Icons.Filled.Science
    HealthType.MEDICACAO -> Icons.Filled.Medication
    HealthType.ODONTOLOGIA -> Icons.Filled.Mood
    HealthType.NUTRICAO -> Icons.Filled.Restaurant
    HealthType.FISIOTERAPIA -> Icons.Filled.Accessibility
    HealthType.TERAPIA -> Icons.Filled.Psychology
    HealthType.TRATAMENTO -> Icons.Filled.Healing
    HealthType.BIOIMPEDANCIA -> Icons.Filled.FitnessCenter
    HealthType.ANAMNESE -> Icons.Filled.Description
    HealthType.OFTALMOLOGIA -> Icons.Filled.Visibility
    HealthType.SUPLEMENTACAO -> Icons.Filled.LocalPharmacy
}

/** Cor de cada tipo, derivada do tema atual. */
internal fun HealthType.color(): Color = when (this) {
    HealthType.CONSULTA -> Evolui.Cyan
    HealthType.EXAME -> Evolui.Purple
    HealthType.MEDICACAO -> Evolui.Orange
    HealthType.ODONTOLOGIA -> lerp(Evolui.Cyan, Evolui.Purple, 0.5f)
    HealthType.NUTRICAO -> Evolui.Neon
    HealthType.FISIOTERAPIA -> lerp(Evolui.Orange, Evolui.Red, 0.5f)
    HealthType.TERAPIA -> lerp(Evolui.Purple, Evolui.Red, 0.5f)
    HealthType.TRATAMENTO -> lerp(Evolui.Neon, Evolui.Cyan, 0.5f)
    HealthType.BIOIMPEDANCIA -> lerp(Evolui.Neon, Evolui.Orange, 0.5f)
    HealthType.ANAMNESE -> Evolui.TextMuted
    HealthType.OFTALMOLOGIA -> lerp(Evolui.Cyan, Evolui.Neon, 0.5f)
    HealthType.SUPLEMENTACAO -> lerp(Evolui.Neon, Evolui.Purple, 0.35f)
}

internal fun minuteText(minute: Int): String = "%02d:%02d".format(minute / 60, minute % 60)

internal fun dayText(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(com.addsoncosta.evolui.ui.components.DateFormat)

/** "Hoje", "Amanhã" ou "qua, 14 de out". */
internal fun relativeDayText(epochDay: Long, today: Long = LocalDate.now().toEpochDay()): String = when (epochDay) {
    today -> "Hoje"
    today + 1 -> "Amanhã"
    today - 1 -> "Ontem"
    else -> LocalDate.ofEpochDay(epochDay).format(com.addsoncosta.evolui.ui.components.DayFormat)
}

/** Primeira letra maiúscula (nomes de mês e dia vêm em minúsculas em pt-BR). */
internal fun String.capitalized(): String = replaceFirstChar { if (it.isLowerCase()) it.titlecase(PtBr) else it.toString() }

/** Normaliza para busca: minúsculas e sem acentos. */
internal fun String.searchKey(): String =
    java.text.Normalizer.normalize(lowercase(PtBr), java.text.Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

internal fun matches(query: String, vararg texts: String?): Boolean {
    val q = query.trim().searchKey()
    if (q.isEmpty()) return true
    return texts.any { it != null && it.searchKey().contains(q) }
}

/** Cabeçalho das telas internas: voltar, título e botão de compartilhar. */
@Composable
internal fun CareHeader(overline: String, title: String, area: ReportArea, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            Overline(overline)
            Text(title, style = MaterialTheme.typography.headlineMedium)
        }
        HealthShareButton(area = area)
    }
}

/** Campo de busca com botão de limpar. */
@Composable
internal fun SearchField(query: String, onQueryChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        leadingIcon = { Icon(Icons.Filled.Search, null) },
        trailingIcon = if (query.isNotEmpty()) {
            { IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Filled.Clear, "Limpar busca") } }
        } else null,
        placeholder = { Text(placeholder) },
    )
}

/** Campo de texto com ajuda (exemplo) embaixo. */
@Composable
internal fun HelpField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    help: String?,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        supportingText = if (help != null) { { Text(help) } } else null,
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 2,
        modifier = modifier.fillMaxWidth(),
    )
}

/** Texto explicativo de lista vazia. */
@Composable
internal fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.fillMaxWidth().padding(vertical = 12.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

/** Pergunta de confirmação (apagar, limpar...). */
@Composable
internal fun ConfirmDialog(title: String, text: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { onDismiss(); onConfirm() }) { Text(confirmLabel, color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** Formulário em tela cheia (mais confortável com fonte grande do que uma folha). */
@Composable
internal fun FormDialog(
    title: String,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    saveEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "Fechar sem salvar") }
                    Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = onSave, enabled = saveEnabled) { Text("Salvar", fontWeight = FontWeight.Bold) }
                }
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    content()
                    Button(onClick = onSave, enabled = saveEnabled, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                        Text("Salvar", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/** Escolha de data. [initialDay] e o resultado são epochDay. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DatePick(initialDay: Long, onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initialDay * 86_400_000L)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()) }
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    ) { DatePicker(state) }
}

/** Escolha de hora (24 h). [initialMinute] e o resultado são minutos desde a meia-noite. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimePick(initialMinute: Int?, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val start = initialMinute ?: (LocalTime.now().hour * 60)
    val state = rememberTimePickerState(initialHour = start / 60, initialMinute = start % 60, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Horário") },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = {
                onPick(state.hour * 60 + state.minute)
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** Marcação de um dia na grade do mês. */
internal data class DayMark(
    val fill: Color? = null,
    val outline: Color? = null,
    val dots: List<Color> = emptyList(),
)

/**
 * Grade de um mês (semana começando no domingo), com setas para trocar de mês.
 * [mark] diz como pintar cada dia (epochDay).
 */
@Composable
internal fun MonthGrid(
    month: YearMonth,
    selectedDay: Long?,
    onMonthChange: (YearMonth) -> Unit,
    onDayClick: (Long) -> Unit,
    mark: (Long) -> DayMark,
) {
    val today = LocalDate.now().toEpochDay()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onMonthChange(month.minusMonths(1)) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Mês anterior") }
            Text(
                month.atDay(1).format(MonthTitleFormat).capitalized(),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).clickable { onMonthChange(YearMonth.now()) },
            )
            IconButton(onClick = { onMonthChange(month.plusMonths(1)) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Próximo mês") }
        }
        val weekStart = listOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY)
        Row(Modifier.fillMaxWidth()) {
            weekStart.forEach { d ->
                Text(
                    d.getDisplayName(TextStyle.NARROW, PtBr).uppercase(PtBr),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val first = month.atDay(1)
        val lead = first.dayOfWeek.value % 7 // domingo = 0
        val days = month.lengthOfMonth()
        val rows = (lead + days + 6) / 7
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (c in 0 until 7) {
                    val dayNumber = r * 7 + c - lead + 1
                    Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                        if (dayNumber in 1..days) {
                            val epochDay = first.plusDays((dayNumber - 1).toLong()).toEpochDay()
                            DayCell(dayNumber, epochDay == today, epochDay == selectedDay, mark(epochDay)) { onDayClick(epochDay) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(day: Int, isToday: Boolean, selected: Boolean, mark: DayMark, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    var m = Modifier.fillMaxSize().clip(CircleShape)
    m = when {
        selected -> m.background(primary)
        mark.fill != null -> m.background(mark.fill)
        else -> m
    }
    val border = when {
        isToday && !selected -> BorderStroke(2.dp, primary)
        mark.outline != null -> BorderStroke(2.dp, mark.outline)
        else -> null
    }
    if (border != null) m = m.border(border, CircleShape)
    Box(m.clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "$day",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isToday || selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            if (mark.dots.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    mark.dots.take(3).forEach { color ->
                        Box(Modifier.size(5.dp).clip(CircleShape).background(if (selected) MaterialTheme.colorScheme.onPrimary else color))
                    }
                }
            }
        }
    }
}

/** Bolinha colorida com ícone do tipo. */
@Composable
internal fun TypeBadge(type: HealthType, modifier: Modifier = Modifier) {
    val color = type.color()
    Box(modifier.size(40.dp).clip(CircleShape).background(color.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
        Icon(type.icon(), null, tint = color)
    }
}
