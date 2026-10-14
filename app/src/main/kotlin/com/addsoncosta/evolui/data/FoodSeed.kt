package com.addsoncosta.evolui.data

import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * Atualiza a base de alimentos em instalações existentes (e completa a instalação nova).
 *
 * Versão 1 (app 0.3.0):
 * - Corrige pela tacoId os 6 alimentos da TACO que vinham zerados. Valores por 100 g:
 *   leite integral (TBCA/USP 7.2, confere com USDA SR 01077), leite desnatado UHT (USDA SR 01085 / TBCA),
 *   iogurte sabor abacaxi (TBCA, iogurte integral de frutas), coco verde cru (polpa, estimativa TBCA).
 *   Sal dietético e sal grosso continuam com 0: sal não tem calorias nem macronutrientes.
 * - Tira espaços sobrando dos nomes e refaz searchName (eles apareciam no topo da lista).
 * - Acrescenta extra_foods.json (source = "EXTRA", identificados pelo nome normalizado).
 * - Marca a densidade dos líquidos e grava as medidas caseiras típicas de portions.json (food_portion, custom = 0).
 *
 * Roda sempre ao abrir o app; quando já está na versão atual, só lê uma preferência.
 */
object FoodSeed {
    private const val VERSION = 1
    private const val KEY = "versao_alimentos"
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun upgrade(context: Context, db: AppDatabase) {
        val prefs = context.getSharedPreferences("configuracoes", Context.MODE_PRIVATE)
        if (prefs.getInt(KEY, 0) >= VERSION) return
        withContext(Dispatchers.IO) {
            val taco = json.decodeFromString<List<SeedTacoFood>>(readAsset(context, "taco.json"))
            val extras = json.decodeFromString<ExtraFoodFile>(readAsset(context, "extra_foods.json")).foods
            val table = json.decodeFromString<PortionTable>(readAsset(context, "portions.json"))
            val dao = db.foodDao()
            db.withTransaction {
                // 1. TACO: valores corrigidos, nomes limpos, origem e densidade.
                val byId = taco.associateBy { it.id }
                val changed = dao.tacoFoods().mapNotNull { food ->
                    val t = byId[food.tacoId] ?: return@mapNotNull null
                    val name = cleanName(t.name)
                    val search = Repository.normalize(name)
                    val updated = food.copy(
                        name = name,
                        searchName = search,
                        category = t.category,
                        kcal = t.kcal,
                        protein = t.protein,
                        fat = t.fat,
                        carb = t.carb,
                        fiber = t.fiber,
                        source = "TACO",
                        densityGPerMl = food.densityGPerMl ?: table.density(search, t.category),
                    )
                    if (updated != food) updated else null
                }
                if (changed.isNotEmpty()) dao.updateAll(changed)

                // 2. Lista extra: insere o que falta e atualiza o que mudou (mantendo id e favorito).
                val existing = dao.extraFoods().associateBy { it.searchName }
                val fresh = mutableListOf<FoodEntity>()
                val updates = mutableListOf<FoodEntity>()
                val seen = HashSet<String>()
                extras.forEach { e ->
                    val name = cleanName(e.name)
                    val search = Repository.normalize(name)
                    if (!seen.add(search)) return@forEach
                    val entity = FoodEntity(
                        category = e.category,
                        name = name,
                        searchName = search,
                        kcal = e.kcal,
                        protein = e.protein,
                        fat = e.fat,
                        carb = e.carb,
                        fiber = e.fiber,
                        densityGPerMl = e.density ?: table.density(search, e.category),
                        source = "EXTRA",
                    )
                    val old = existing[search]
                    if (old == null) {
                        fresh += entity
                    } else {
                        val merged = entity.copy(id = old.id, favorite = old.favorite)
                        if (merged != old) updates += merged
                    }
                }
                if (fresh.isNotEmpty()) dao.insertAll(fresh)
                if (updates.isNotEmpty()) dao.updateAll(updates)

                // 3. Medidas caseiras típicas (as do usuário ficam).
                val portionDao = db.foodPortionDao()
                portionDao.clearDefaults()
                val extraPortions = extras.associate { Repository.normalize(cleanName(it.name)) to it.portions }
                val portions = dao.catalog().flatMap { food ->
                    val specs = (if (food.source == "EXTRA") extraPortions[food.searchName] else null)
                        ?: table.portionsFor(food.searchName, food.category)
                    specs.mapNotNull { spec ->
                        val grams = spec.g ?: spec.ml?.let { it * (food.densityGPerMl ?: 1.0) } ?: return@mapNotNull null
                        FoodPortionEntity(foodId = food.id, name = spec.name, grams = Portions.round1(grams))
                    }
                }
                if (portions.isNotEmpty()) portionDao.insertAll(portions)
            }
            prefs.edit().putInt(KEY, VERSION).apply()
        }
    }

    /** Sem espaços nas pontas, sem espaço duplo e com espaço depois da vírgula. */
    fun cleanName(name: String): String =
        name.trim().replace(Regex("\\s+"), " ").replace(Regex(",(?=\\S)"), ", ")

    private fun readAsset(context: Context, name: String) =
        context.assets.open(name).bufferedReader().use { it.readText() }
}

@Serializable
private data class SeedTacoFood(
    val id: Int,
    val category: String,
    val name: String,
    val kcal: Double,
    val protein: Double,
    val fat: Double,
    val carb: Double,
    val fiber: Double,
)
