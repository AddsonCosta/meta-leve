package com.addsoncosta.evolui.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Green = Color(0xFF2E7D5B)
private val GreenLight = Color(0xFF7FD8A8)
private val Amber = Color(0xFFF2A541)

private val LightColors = lightColorScheme(primary = Green, secondary = Amber, tertiary = Color(0xFF3B6EA8))
private val DarkColors = darkColorScheme(primary = GreenLight, secondary = Amber, tertiary = Color(0xFF9CC3F0))

@Composable
fun EvoluiTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
