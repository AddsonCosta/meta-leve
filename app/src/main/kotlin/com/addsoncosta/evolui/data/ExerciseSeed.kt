package com.addsoncosta.evolui.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Acrescenta exercícios novos em instalações existentes.
 *
 * Os valores MET de assets/exercises.json vêm do Compendium of Physical Activities
 * (Herrmann et al., 2024 Adult Compendium; e Ainsworth et al., 2011), arredondados.
 * Atividades sem código próprio no Compendium (ex.: beach tennis, futevôlei, padel)
 * usam o valor da atividade mais parecida.
 */
object ExerciseSeed {
    /** Suba este número sempre que mudar a lista de exercícios. */
    private const val VERSION = 2
    private const val KEY = "versao_exercicios"

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun upgrade(context: Context, db: AppDatabase) {
        withContext(Dispatchers.IO) {
            val prefs = context.getSharedPreferences("configuracoes", Context.MODE_PRIVATE)
            if (prefs.getInt(KEY, 0) >= VERSION) return@withContext
            val text = context.assets.open("exercises.json").bufferedReader().use { it.readText() }
            val catalog = json.decodeFromString(catalogSerializer, text)
            val dao = db.exerciseDao()
            val existing = dao.names().map { Repository.normalize(it) }.toSet()
            val fresh = catalog.filter { Repository.normalize(it.name) !in existing }
                .map { ExerciseEntity(category = it.category, name = it.name, met = it.met) }
            if (fresh.isNotEmpty()) dao.insertAll(fresh)
            // Os exercícios antigos passam para as categorias novas (favoritos continuam).
            catalog.forEach { dao.updateCategory(it.name, it.category) }
            prefs.edit().putInt(KEY, VERSION).apply()
        }
    }

    private val catalogSerializer by lazy { ListSerializer(CatalogExercise.serializer()) }

    @Serializable
    private data class CatalogExercise(val category: String, val name: String, val met: Double)
}
