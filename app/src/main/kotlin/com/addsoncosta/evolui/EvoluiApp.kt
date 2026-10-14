package com.addsoncosta.evolui

import android.app.Application
import com.addsoncosta.evolui.data.AppDatabase
import com.addsoncosta.evolui.data.AppSettings
import com.addsoncosta.evolui.data.Repository
import com.addsoncosta.evolui.health.HealthSync
import com.addsoncosta.evolui.ui.theme.Evolui
import com.addsoncosta.evolui.ui.theme.Palettes
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class EvoluiApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Evolui.palette = Palettes.byKey(container.settings.theme.value)
        container.scope.launch { container.repository.seedIfNeeded() }
    }
}

/** Dependências do app, criadas uma vez. */
class AppContainer(app: Application) {
    val scope = CoroutineScope(SupervisorJob())
    val database = AppDatabase.create(app)
    val settings = AppSettings(app)
    val repository = Repository(app, database)
    val health = HealthSync(app)

    /** Dia selecionado na tela Hoje; alimentos e exercícios são registrados nele. */
    val selectedDate = MutableStateFlow(LocalDate.now())
}
