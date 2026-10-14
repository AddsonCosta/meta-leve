package com.addsoncosta.evolui.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.addsoncosta.evolui.AppContainer
import com.addsoncosta.evolui.EvoluiApp
import com.addsoncosta.evolui.core.Profile
import com.addsoncosta.evolui.ui.onboarding.OnboardingScreen
import com.addsoncosta.evolui.ui.theme.EvoluiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EvoluiTheme {
                // Fundo do tema por baixo de tudo, para o texto não sumir no modo escuro.
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Root()
                }
            }
        }
    }
}

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
