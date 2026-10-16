package com.addsoncosta.evolui.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.addsoncosta.evolui.ui.appContainer
import com.addsoncosta.evolui.ui.components.Panel
import com.addsoncosta.evolui.ui.theme.Evolui
import com.addsoncosta.evolui.ui.theme.Palette
import com.addsoncosta.evolui.ui.theme.Palettes
import com.addsoncosta.evolui.ui.theme.Section
import com.addsoncosta.evolui.ui.theme.rememberThemeBackdrop
import kotlin.math.roundToInt

private val PreviewW = 56.dp
private val PreviewH = 80.dp

/** Escolha do tema de cores, em grupos. A troca vale na hora, no app inteiro. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThemePicker() {
    val settings = appContainer().settings
    val current by settings.theme.collectAsStateWithLifecycle()
    val selected = Palettes.byKey(current)
    val byGroup = remember { Palettes.all.groupBy { it.group } }

    Panel {
        Text("Aparência", style = MaterialTheme.typography.titleMedium)
        Text(
            "Escolha as cores e o fundo do app. Você pode trocar quando quiser.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Palettes.groups.forEach { group ->
            val palettes = byGroup[group].orEmpty()
            if (palettes.isNotEmpty()) {
                Text(
                    group,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 6.dp),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    palettes.forEach { palette ->
                        ThemeSwatch(palette, palette.key == selected.key) {
                            settings.setTheme(palette.key)
                            Evolui.palette = palette
                        }
                    }
                }
            }
        }
    }
}

/** Miniatura do tema: o fundo de verdade (em cache), um cartão com texto e a cor principal. */
@Composable
private fun ThemeSwatch(palette: Palette, selected: Boolean, onClick: () -> Unit) {
    val density = LocalDensity.current
    val wPx = with(density) { PreviewW.roundToPx() }
    val hPx = with(density) { PreviewH.roundToPx() }
    val image = rememberThemeBackdrop(palette, Section.TODAY, wPx, hPx, maxSide = 320)
    val shape = RoundedCornerShape(14.dp)
    val card = if (palette.glass) palette.surface.copy(alpha = 0.70f) else palette.surface
    Column(
        Modifier.width(64.dp).clip(RoundedCornerShape(16.dp)).clickable(role = Role.RadioButton, onClick = onClick).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(PreviewW, PreviewH)
                .clip(shape)
                .drawBehind {
                    drawRect(palette.background)
                    if (image != null) {
                        drawImage(
                            image,
                            srcOffset = IntOffset.Zero,
                            srcSize = IntSize(image.width, image.height),
                            dstOffset = IntOffset.Zero,
                            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                            filterQuality = FilterQuality.Low,
                        )
                    }
                }
                .border(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    shape = shape,
                ),
        ) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(card)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            ) {
                Text("Aa", color = palette.text, fontSize = 12.sp, fontWeight = FontWeight.Bold, lineHeight = 14.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.padding(top = 2.dp)) {
                    listOf(palette.accent, palette.accent2, palette.accent3).forEach {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(it))
                    }
                }
            }
            if (selected) {
                Icon(
                    Icons.Filled.Check, null, tint = palette.onAccent,
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(18.dp).clip(CircleShape).background(palette.accent),
                )
            }
        }
        Text(
            palette.label,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
