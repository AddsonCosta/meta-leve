package com.addsoncosta.evolui.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Preferências simples do app (tema, metas manuais), guardadas no celular. */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("configuracoes", Context.MODE_PRIVATE)

    private val _theme = MutableStateFlow(prefs.getString(KEY_THEME, null))
    /** Chave do tema escolhido (ver Palettes); nulo = padrão. */
    val theme: StateFlow<String?> = _theme.asStateFlow()

    fun setTheme(key: String) {
        prefs.edit().putString(KEY_THEME, key).apply()
        _theme.value = key
    }

    private val _waterGoalMl = MutableStateFlow(prefs.getFloat(KEY_WATER_GOAL, 0f).toDouble().takeIf { it > 0 })
    /** Meta de água escolhida pelo usuário, em ml; nulo = calcular pelo peso. */
    val waterGoalMl: StateFlow<Double?> = _waterGoalMl.asStateFlow()

    fun setWaterGoalMl(ml: Double?) {
        prefs.edit().putFloat(KEY_WATER_GOAL, (ml ?: 0.0).toFloat()).apply()
        _waterGoalMl.value = ml?.takeIf { it > 0 }
    }

    /** Versão dos dados de alimentos/exercícios já carregados (para acrescentar itens novos em atualizações). */
    var seedVersion: Int
        get() = prefs.getInt(KEY_SEED, 0)
        set(value) = prefs.edit().putInt(KEY_SEED, value).apply()

    /** Gravar no Health Connect os treinos lançados no Evolui. */
    var writeWorkoutsToHealth: Boolean
        get() = prefs.getBoolean(KEY_WRITE_HC, true)
        set(value) = prefs.edit().putBoolean(KEY_WRITE_HC, value).apply()

    private companion object {
        const val KEY_THEME = "tema"
        const val KEY_WATER_GOAL = "meta_agua_ml"
        const val KEY_SEED = "versao_dados"
        const val KEY_WRITE_HC = "gravar_treinos_health"
    }
}
