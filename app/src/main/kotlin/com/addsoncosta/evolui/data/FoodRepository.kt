package com.addsoncosta.evolui.data

import androidx.room.withTransaction
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn

/** Valores de um alimento para uma quantidade em gramas. */
data class Nutrition(val kcal: Double, val protein: Double, val fat: Double, val carb: Double, val fiber: Double)

fun FoodEntity.nutritionFor(grams: Double): Nutrition {
    val f = grams / 100
    return Nutrition(kcal * f, protein * f, fat * f, carb * f, fiber * f)
}

/** Refeição salva com seus itens. */
data class SavedMeal(val meal: SavedMealEntity, val items: List<SavedMealItemEntity>) {
    val kcal: Double get() = items.sumOf { it.kcal }
    val type: MealType get() = mealTypeOf(meal.mealType)
}

fun mealTypeOf(name: String): MealType = MealType.entries.firstOrNull { it.name == name } ?: MealType.SNACK

/** Alimentos, medidas e refeições salvas (tela Comer). */
class FoodRepository(private val db: AppDatabase) {
    private val foods = db.foodDao()
    private val saved = db.savedMealDao()

    /**
     * Busca por palavras em qualquer ordem e sem acento ("arroz cozido" acha "Arroz, tipo 1, cozido"), aceitando
     * plural e feminino ("bananas", "carne moida"). Palavras gerais ("peixe", "fruta", "doce", "frutos do mar") trazem
     * também os alimentos do grupo (ver [FoodSynonyms]). Ordem: nome com as palavras exatas, nome com as palavras
     * em outra forma, alimentos do grupo e, por último, nomes que só têm a palavra por acaso ("batata doce" em "doce").
     * Dentro de cada parte: favoritos, os que começam com a primeira palavra e ordem alfabética.
     */
    fun search(query: String): Flow<List<FoodEntity>> {
        val parsed = FoodSynonyms.parse(Repository.normalize(query))
        val tokens = parsed.tokens
        if (tokens.isEmpty()) return flowOf(emptyList())
        val first = tokens.first()
        val main = tokens.maxBy { it.length }
        val variants = tokens.map { FoodSynonyms.variants(it) }
        val restVariants = parsed.rest.map { FoodSynonyms.variants(it) }
        val groups = parsed.groups
        // Poucas consultas pequenas no banco (com LIKE e LIMIT), nunca a tabela inteira.
        val sources = buildList<Flow<List<FoodEntity>>> {
            FoodSynonyms.variants(main).forEach { add(foods.searchToken(it)) }
            groups.firstOrNull()?.let { g ->
                if (g.categories.isNotEmpty()) add(foods.inCategories(g.categories))
                g.likeTokens.forEach { add(foods.searchToken(it)) }
            }
        }

        fun rank(food: FoodEntity): Int? {
            val s = food.searchName
            val exact = tokens.all { s.contains(it) }
            val loose = exact || variants.all { forms -> forms.any { s.contains(it) } }
            if (loose) return if (groups.any { it.excludes(food) }) 3 else if (exact) 0 else 1
            val inGroup = groups.isNotEmpty() && groups.all { it.matches(food) } &&
                restVariants.all { forms -> forms.any { s.contains(it) } }
            return if (inGroup) 2 else null
        }

        return combine(sources) { lists ->
            val seen = HashSet<Long>()
            val ranked = ArrayList<Pair<Int, FoodEntity>>()
            lists.forEach { list ->
                list.forEach { food -> if (seen.add(food.id)) rank(food)?.let { ranked += it to food } }
            }
            ranked.sortedWith(
                compareBy<Pair<Int, FoodEntity>> { it.first }
                    .thenByDescending { it.second.favorite }
                    .thenByDescending { it.second.searchName.startsWith(first) }
                    .thenBy { it.second.searchName }
            )
                .take(150)
                .map { it.second }
        }.flowOn(Dispatchers.Default)
    }

    /** Alimentos pelo nome sem acento, na ordem pedida (os que não existem ficam de fora). */
    suspend fun foodsByNames(names: List<String>): List<FoodEntity> {
        if (names.isEmpty()) return emptyList()
        val found = foods.byNames(names.distinct()).associateBy { it.searchName }
        return names.distinct().mapNotNull { found[it] }
    }

    /** Primeiro resultado da busca (para nomes vindos de fora, ex.: um estimador de pratos). */
    suspend fun bestMatch(query: String): FoodEntity? = search(query).first().firstOrNull()

    suspend fun portionsOnce(foodId: Long): List<FoodPortionEntity> = db.foodPortionDao().observeForFood(foodId).first()

    fun favorites(): Flow<List<FoodEntity>> = foods.observeFavoritesSorted()

    fun recents(limit: Int): Flow<List<FoodEntity>> = foods.observeRecent(limit)

    fun browse(category: String?): Flow<List<FoodEntity>> = foods.observeBrowse(category)

    fun categories(): Flow<List<String>> = foods.observeCategories()

    suspend fun food(id: Long): FoodEntity? = foods.get(id)

    // Medidas

    fun portions(foodId: Long): Flow<List<FoodPortionEntity>> = db.foodPortionDao().observeForFood(foodId)

    suspend fun addPortion(foodId: Long, name: String, grams: Double): Long =
        db.foodPortionDao().insert(FoodPortionEntity(foodId = foodId, name = name.trim(), grams = grams, custom = true))

    suspend fun deletePortion(portion: FoodPortionEntity) = db.foodPortionDao().delete(portion)

    suspend fun lastMeal(foodId: Long): MealEntity? = db.mealDao().lastForFood(foodId)

    // Registro

    suspend fun log(date: LocalDate, food: FoodEntity, choice: PortionChoice, mealType: MealType) {
        db.mealDao().insert(mealFor(date, food, choice, mealType))
    }

    /** Registra vários alimentos de uma vez (prato por foto). */
    suspend fun logAll(date: LocalDate, items: List<Pair<FoodEntity, PortionChoice>>, mealType: MealType) {
        if (items.isEmpty()) return
        db.mealDao().insertAll(items.map { (food, choice) -> mealFor(date, food, choice, mealType) })
    }

    suspend fun dayMeals(date: LocalDate): List<MealEntity> = db.mealDao().dayOnce(date.toEpochDay())

    // Refeições salvas

    fun savedMeals(): Flow<List<SavedMeal>> = combine(saved.observeAll(), saved.observeAllItems()) { meals, items ->
        val byMeal = items.groupBy { it.savedMealId }
        meals.map { SavedMeal(it, byMeal[it.id].orEmpty()) }
    }

    /** Grava (ou atualiza) a refeição salva com estes itens. Devolve o id. */
    suspend fun saveMeal(meal: SavedMealEntity, items: List<SavedMealItemEntity>): Long = db.withTransaction {
        // REPLACE devolve o id da linha, nova ou atualizada.
        val id = saved.insert(meal)
        saved.deleteItems(id)
        saved.insertItems(items.map { it.copy(id = 0, savedMealId = id) })
        id
    }

    suspend fun saveDayAsMeal(date: LocalDate, type: MealType, name: String): Long {
        val items = dayMeals(date).filter { it.mealType == type.name }.map { itemFrom(it) }
        return saveMeal(SavedMealEntity(name = name.trim(), mealType = type.name, createdMillis = System.currentTimeMillis()), items)
    }

    suspend fun deleteSavedMeal(mealId: Long) {
        db.withTransaction {
            saved.deleteItems(mealId)
            saved.deleteById(mealId)
        }
    }

    /** Registra todos os itens da refeição salva no dia escolhido. */
    suspend fun logSavedMeal(meal: SavedMeal, date: LocalDate, type: MealType) {
        db.mealDao().insertAll(
            meal.items.map {
                MealEntity(
                    epochDay = date.toEpochDay(),
                    foodId = it.foodId,
                    name = it.name,
                    grams = it.grams,
                    mealType = type.name,
                    kcal = it.kcal,
                    protein = it.protein,
                    fat = it.fat,
                    carb = it.carb,
                    amount = it.amount,
                    unit = it.unit,
                )
            }
        )
    }

    companion object {
        fun mealFor(date: LocalDate, food: FoodEntity, choice: PortionChoice, mealType: MealType): MealEntity {
            val n = food.nutritionFor(choice.grams)
            return MealEntity(
                epochDay = date.toEpochDay(),
                foodId = food.id,
                name = food.name,
                grams = choice.grams,
                mealType = mealType.name,
                kcal = n.kcal,
                protein = n.protein,
                fat = n.fat,
                carb = n.carb,
                amount = choice.amount,
                unit = choice.unit,
            )
        }

        fun item(food: FoodEntity, choice: PortionChoice): SavedMealItemEntity {
            val n = food.nutritionFor(choice.grams)
            return SavedMealItemEntity(
                savedMealId = 0,
                foodId = food.id,
                name = food.name,
                grams = choice.grams,
                kcal = n.kcal,
                protein = n.protein,
                fat = n.fat,
                carb = n.carb,
                amount = choice.amount,
                unit = choice.unit,
            )
        }

        fun itemFrom(meal: MealEntity) = SavedMealItemEntity(
            savedMealId = 0,
            foodId = meal.foodId,
            name = meal.name,
            grams = meal.grams,
            kcal = meal.kcal,
            protein = meal.protein,
            fat = meal.fat,
            carb = meal.carb,
            amount = meal.amount,
            unit = meal.unit,
        )

        /** Item com outra quantidade em gramas, mantendo a proporção dos valores. */
        fun scaled(item: SavedMealItemEntity, grams: Double): SavedMealItemEntity {
            val f = if (item.grams > 0) grams / item.grams else 0.0
            return item.copy(
                grams = grams,
                kcal = item.kcal * f,
                protein = item.protein * f,
                fat = item.fat * f,
                carb = item.carb * f,
                amount = null,
                unit = null,
            )
        }
    }
}
