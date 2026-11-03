package com.addsoncosta.evolui.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import com.addsoncosta.evolui.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.addsoncosta.evolui.i18n.AppLanguage
import com.addsoncosta.evolui.i18n.I18n
import com.addsoncosta.evolui.ui.theme.Evolui
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Idioma do app (o nome vem de quando só havia português); segue a escolha em Configurações. */
val PtBr: Locale get() = I18n.locale

/** Formatos de data de cada idioma, criados uma vez. */
private fun perLanguage(pattern: (AppLanguage) -> String): Map<AppLanguage, DateTimeFormatter> =
    AppLanguage.entries.associateWith { DateTimeFormatter.ofPattern(pattern(it), it.locale) }

private val dateFormats = perLanguage { "dd/MM/yyyy" }
private val shortDateFormats = perLanguage { "dd/MM" }
private val dayFormats = perLanguage {
    when (it) {
        AppLanguage.PT, AppLanguage.ES -> "EEE, d 'de' MMM"
        AppLanguage.EN -> "EEE, MMM d"
        AppLanguage.DE -> "EEE, d. MMM"
        AppLanguage.FR, AppLanguage.IT -> "EEE d MMM"
    }
}
private val timeFormats = perLanguage { "HH:mm" }
private val monthTitleFormats = perLanguage { if (it == AppLanguage.PT || it == AppLanguage.ES) "MMMM 'de' yyyy" else "MMMM yyyy" }
private val longDayFormats = perLanguage {
    when (it) {
        AppLanguage.PT, AppLanguage.ES -> "EEEE, d 'de' MMMM"
        AppLanguage.EN -> "EEEE, MMMM d"
        AppLanguage.DE -> "EEEE, d. MMMM"
        AppLanguage.FR, AppLanguage.IT -> "EEEE d MMMM"
    }
}

val DateFormat: DateTimeFormatter get() = dateFormats.getValue(I18n.language)
val ShortDateFormat: DateTimeFormatter get() = shortDateFormats.getValue(I18n.language)
val DayFormat: DateTimeFormatter get() = dayFormats.getValue(I18n.language)
val TimeFormat: DateTimeFormatter get() = timeFormats.getValue(I18n.language)
val MonthYearFormat: DateTimeFormatter get() = monthTitleFormats.getValue(I18n.language)
val LongDayFormat: DateTimeFormatter get() = longDayFormats.getValue(I18n.language)

fun Double.fmt(decimals: Int = 0): String = NumberFormat.getNumberInstance(PtBr).apply {
    maximumFractionDigits = decimals
    minimumFractionDigits = 0
}.format(this)

/** Converte texto digitado com vírgula ou ponto. */
fun String.toDecimal(): Double? = trim().replace(',', '.').toDoubleOrNull()

/**
 * Campo numérico. [help] aparece embaixo da caixa (ex.: um exemplo de preenchimento);
 * [error], quando presente, substitui a ajuda em vermelho.
 */
@Composable
fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    decimal: Boolean = true,
    help: String? = null,
    error: String? = null,
    highlight: Boolean = false,
) {
    val invalid = error != null || (value.isNotEmpty() && value.toDecimal() == null)
    // Campo que a leitura automática não conseguiu preencher: leve destaque e "Preencher".
    val mark = highlight && !invalid && value.isEmpty()
    val below = error ?: if (mark) (if (help.isNullOrBlank()) "Preencher" else "Preencher · $help") else help
    val tint = MaterialTheme.colorScheme.tertiary
    OutlinedTextField(
        colors = if (mark) {
            OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = tint.copy(alpha = 0.10f),
                focusedContainerColor = tint.copy(alpha = 0.06f),
                unfocusedBorderColor = tint.copy(alpha = 0.85f),
                unfocusedLabelColor = tint,
                unfocusedSupportingTextColor = tint,
                focusedSupportingTextColor = tint,
            )
        } else {
            OutlinedTextFieldDefaults.colors()
        },
        value = value,
        onValueChange = { new -> if (new.all { it.isDigit() || (decimal && (it == ',' || it == '.')) }) onValueChange(new) },
        label = { Text(label) },
        suffix = if (suffix != null) { { Text(suffix) } } else null,
        supportingText = if (below != null) { { Text(below) } } else null,
        singleLine = true,
        isError = invalid,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = modifier,
    )
}

/** Cartão escuro arredondado usado em todas as telas. */
@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        // Temas de vidro: uma borda clara e fina, como a aresta de um vidro.
        border = if (Evolui.palette.glass) BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)) else null,
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

/** Título de seção em caixa alta, discreto. */
@Composable
fun Overline(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text.uppercase(PtBr), modifier = modifier, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.Bold)
}

/** Anel de progresso (0 a 1), com o conteúdo no centro. Passa de 1 desenhando a volta extra em cor de alerta. */
@Composable
fun Ring(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 200.dp,
    stroke: Dp = 18.dp,
    overColor: Color = MaterialTheme.colorScheme.error,
    content: @Composable () -> Unit = {},
) {
    val track = color.copy(alpha = 0.16f)
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val s = stroke.toPx()
            val d = this.size.minDimension - s
            val topLeft = Offset(s / 2, s / 2)
            val arc = Size(d, d)
            drawArc(track, 0f, 360f, false, topLeft, arc, style = Stroke(s))
            val p = progress.coerceAtLeast(0f)
            drawArc(color, -90f, 360f * p.coerceAtMost(1f), false, topLeft, arc, style = Stroke(s, cap = StrokeCap.Round))
            if (p > 1f) {
                drawArc(overColor, -90f, 360f * (p - 1f).coerceAtMost(1f), false, topLeft, arc, style = Stroke(s, cap = StrokeCap.Round))
            }
        }
        content()
    }
}

/** Anel pequeno com rótulo embaixo, para macronutrientes e afins. */
@Composable
fun MiniRing(label: String, value: String, progress: Float, color: Color, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Ring(progress, color, size = 76.dp, stroke = 8.dp) {
            Text("${(progress * 100).toDouble().fmt()}%", style = MaterialTheme.typography.labelLarge)
        }
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Text(value, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Número grande com legenda, para linhas de estatísticas. */
@Composable
fun Stat(value: String, label: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Linha com rótulo à esquerda e valor à direita. O rótulo usa a largura natural (com limite),
 * e o valor ocupa o resto, quebrando linha alinhado à direita. Assim um valor longo nunca
 * espreme o rótulo em uma letra por linha.
 */
@Composable
fun KeyValue(key: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(
            key,
            Modifier.widthIn(max = 160.dp).padding(end = 12.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, Modifier.weight(1f), fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
    }
}
