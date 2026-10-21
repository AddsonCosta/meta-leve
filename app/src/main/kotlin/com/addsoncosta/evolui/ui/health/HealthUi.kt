package com.addsoncosta.evolui.ui.health

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material.icons.filled.LocalPharmacy
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SentimentSatisfied
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.addsoncosta.evolui.data.HealthRepository
import com.addsoncosta.evolui.data.HealthType
import com.addsoncosta.evolui.ui.appContainer
import com.addsoncosta.evolui.ui.components.DateFormat
import com.addsoncosta.evolui.ui.components.Overline
import com.addsoncosta.evolui.ui.theme.Evolui
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Repositório da Saúde, criado uma vez por tela. */
@Composable
fun rememberHealthRepository(): HealthRepository {
    val container = appContainer()
    val context = LocalContext.current
    return remember(container) { HealthRepository(context.applicationContext, container.database) }
}

fun dayText(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(DateFormat)

/** Cor de cada área (segue o tema escolhido). */
@Composable
fun HealthType.color(): Color = when (this) {
    HealthType.CONSULTA -> Evolui.Cyan
    HealthType.EXAME -> Evolui.Purple
    HealthType.MEDICACAO -> Evolui.Orange
    HealthType.ODONTOLOGIA -> Evolui.Cyan
    HealthType.NUTRICAO -> Evolui.Neon
    HealthType.FISIOTERAPIA -> Evolui.Orange
    HealthType.TERAPIA -> Evolui.Purple
    HealthType.TRATAMENTO -> Evolui.Red
    HealthType.BIOIMPEDANCIA -> Evolui.Neon
    HealthType.ANAMNESE -> Evolui.Cyan
    HealthType.OFTALMOLOGIA -> Evolui.Cyan
    HealthType.SUPLEMENTACAO -> Evolui.Neon
}

fun HealthType.icon(): ImageVector = when (this) {
    HealthType.CONSULTA -> Icons.Filled.MedicalServices
    HealthType.EXAME -> Icons.Filled.Science
    HealthType.MEDICACAO -> Icons.Filled.Medication
    HealthType.ODONTOLOGIA -> Icons.Filled.SentimentSatisfied
    HealthType.NUTRICAO -> Icons.Filled.Restaurant
    HealthType.FISIOTERAPIA -> Icons.Filled.AccessibilityNew
    HealthType.TERAPIA -> Icons.Filled.Psychology
    HealthType.TRATAMENTO -> Icons.Filled.Healing
    HealthType.BIOIMPEDANCIA -> Icons.Filled.MonitorWeight
    HealthType.ANAMNESE -> Icons.Filled.ContentPaste
    HealthType.OFTALMOLOGIA -> Icons.Filled.Visibility
    HealthType.SUPLEMENTACAO -> Icons.Filled.LocalPharmacy
}

val CalendarIcon: ImageVector get() = Icons.Filled.CalendarMonth

/** Cabeçalho das telas internas: voltar, título e ações à direita. */
@Composable
fun BackHeader(overline: String, title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
        Column(Modifier.weight(1f)) {
            Overline(overline)
            Text(title, style = MaterialTheme.typography.headlineMedium)
        }
        actions()
    }
}

/** Caixa de busca com botão de limpar. */
@Composable
fun SearchField(query: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = query,
        onValueChange = onChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        leadingIcon = { Icon(Icons.Filled.Search, null) },
        trailingIcon = if (query.isNotEmpty()) { { IconButton(onClick = { onChange("") }) { Icon(Icons.Filled.Clear, "Limpar busca") } } } else null,
        placeholder = { Text(placeholder) },
        shape = MaterialTheme.shapes.medium,
    )
}

/** Campo de texto com ajuda (exemplo) embaixo. */
@Composable
fun HelpField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    help: String?,
    modifier: Modifier = Modifier,
    multiline: Boolean = false,
    error: String? = null,
    number: Boolean = false,
    highlight: Boolean = false,
) {
    // Campo que a leitura do documento não conseguiu preencher: leve destaque e "Preencher".
    val mark = highlight && error == null && value.isBlank()
    val below = error ?: if (mark) fillHint(help) else help
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        supportingText = if (below != null) { { Text(below) } } else null,
        isError = error != null,
        colors = if (mark) fillColors() else OutlinedTextFieldDefaults.colors(),
        singleLine = !multiline,
        minLines = if (multiline) 3 else 1,
        keyboardOptions = KeyboardOptions(
            capitalization = if (number) KeyboardCapitalization.None else KeyboardCapitalization.Sentences,
            keyboardType = if (number) KeyboardType.Number else KeyboardType.Text,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

/** Legenda de campo a preencher (a leitura do documento não achou o valor). */
fun fillHint(help: String?): String = if (help.isNullOrBlank()) "Preencher" else "Preencher · $help"

/** Cores do destaque leve dos campos que faltam preencher (cor de destaque do tema). */
@Composable
fun fillColors(): TextFieldColors {
    val c = MaterialTheme.colorScheme.tertiary
    return OutlinedTextFieldDefaults.colors(
        unfocusedContainerColor = c.copy(alpha = 0.10f),
        focusedContainerColor = c.copy(alpha = 0.06f),
        unfocusedBorderColor = c.copy(alpha = 0.85f),
        unfocusedLabelColor = c,
        unfocusedSupportingTextColor = c,
        focusedSupportingTextColor = c,
    )
}

/** Campo de data: toca e abre o calendário. [epochDay] nulo = vazio. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    label: String,
    epochDay: Long?,
    onChange: (Long?) -> Unit,
    help: String?,
    modifier: Modifier = Modifier,
    clearable: Boolean = false,
    highlight: Boolean = false,
) {
    var open by remember { mutableStateOf(false) }
    val below = if (highlight) fillHint(help) else help
    Box(modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = epochDay?.let { dayText(it) } ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            placeholder = { Text("Toque para escolher") },
            supportingText = if (below != null) { { Text(below) } } else null,
            colors = if (highlight) fillColors() else OutlinedTextFieldDefaults.colors(),
            trailingIcon = {
                if (clearable && epochDay != null) {
                    IconButton(onClick = { onChange(null) }) { Icon(Icons.Filled.Clear, "Limpar data") }
                } else {
                    Icon(Icons.Filled.CalendarMonth, null)
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        // Camada por cima do campo para abrir o calendário com um toque (deixa o botão de limpar livre).
        Box(
            Modifier
                .matchParentSize()
                .padding(end = if (clearable && epochDay != null) 56.dp else 0.dp)
                .clickable { open = true },
        )
    }
    if (open) {
        val initial = (epochDay ?: LocalDate.now().toEpochDay()) * 86_400_000L
        val state = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()) }
                    open = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancelar") } },
        ) { DatePicker(state) }
    }
}

/** Pergunta antes de apagar. */
@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirm, color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** Texto explicativo para listas vazias. */
@Composable
fun EmptyNote(title: String, text: String) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
