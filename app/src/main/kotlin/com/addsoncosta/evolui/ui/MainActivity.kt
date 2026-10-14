package com.addsoncosta.evolui.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.addsoncosta.evolui.AppContainer
import com.addsoncosta.evolui.EvoluiApp
import com.addsoncosta.evolui.core.Profile
import com.addsoncosta.evolui.ui.onboarding.OnboardingScreen
import com.addsoncosta.evolui.ui.theme.Evolui
import com.addsoncosta.evolui.ui.theme.EvoluiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Pedido do Health Connect para mostrar a política de privacidade.
        if (intent?.action in PrivacyActions) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_URL)))
            finish()
            return
        }
        setContent {
            // Ícones da barra de status claros em temas escuros e escuros em temas claros.
            val dark = Evolui.palette.dark
            LaunchedEffect(dark) {
                val transparent = android.graphics.Color.TRANSPARENT
                val bars = if (dark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent)
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
            }
            EvoluiTheme {
                // Fundo do tema por baixo de tudo, para o texto nunca ficar sem contraste.
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Root()
                }
            }
        }
    }
}

private const val PRIVACY_URL = "https://github.com/AddsonCosta/meta-leve/blob/main/docs/privacidade.md"
private val PrivacyActions = setOf("androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE", "android.intent.action.VIEW_PERMISSION_USAGE")

@Composable
fun appContainer(): AppContainer = (LocalContext.current.applicationContext as EvoluiApp).container

/** Mostra o cadastro inicial até existir um perfil; depois, o app. */
@Composable
private fun Root() {
    val container = appContainer()
    val state by produceState<ProfileState>(ProfileState.Loading) {
        container.repository.profile.collect { value = if (it == null) ProfileState.Missing else ProfileState.Ready(it) }
    }
    when (val s = state) {
        ProfileState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        ProfileState.Missing -> OnboardingScreen(initial = null, onDone = {})
        is ProfileState.Ready -> MainScaffold()
    }
}

private sealed interface ProfileState {
    data object Loading : ProfileState
    data object Missing : ProfileState
    data class Ready(val profile: Profile) : ProfileState
}
