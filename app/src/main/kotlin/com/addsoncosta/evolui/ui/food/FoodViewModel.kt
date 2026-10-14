package com.addsoncosta.evolui.ui.food

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.addsoncosta.evolui.AppContainer
import com.addsoncosta.evolui.data.FoodEntity
import com.addsoncosta.evolui.data.FoodPortionEntity
import com.addsoncosta.evolui.data.FoodRepository
import com.addsoncosta.evolui.data.MealEntity
import com.addsoncosta.evolui.data.MealType
import com.addsoncosta.evolui.data.PortionChoice
import com.addsoncosta.evolui.data.SavedMeal
import com.addsoncosta.evolui.data.SavedMealEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Resultado de uma busca, com o texto que a gerou (para não mostrar "nada encontrado" antes da hora). */
data class FoodSearch(val query: String, val items: List<FoodEntity>)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class FoodViewModel(private val container: AppContainer) : ViewModel() {
    private val repo = container.repository
    val foods = FoodRepository(container.database)
    val date = container.selectedDate
    val query = MutableStateFlow("")

    /** Categoria escolhida na lista completa (nulo = todas). */
    val category = MutableStateFlow<String?>(null)

    val search: StateFlow<FoodSearch> = query
        .debounce(150)
        .map { it.trim() }
        .distinctUntilChanged()
        .flatMapLatest { q -> if (q.isEmpty()) flowOf(FoodSearch(q, emptyList())) else foods.search(q).map { FoodSearch(q, it) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FoodSearch("", emptyList()))

    val results: StateFlow<List<FoodEntity>> = search
        .map { it.items }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val favorites: StateFlow<List<FoodEntity>> = foods.favorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recents: StateFlow<List<FoodEntity>> = foods.recents(8)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val browse: StateFlow<List<FoodEntity>> = category
        .flatMapLatest { foods.browse(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categories: StateFlow<List<String>> = foods.categories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val savedMeals: StateFlow<List<SavedMeal>> = foods.savedMeals()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** O que já foi registrado no dia selecionado (para "Salvar refeição de hoje"). */
    val dayMeals: StateFlow<List<MealEntity>> = date
        .flatMapLatest { repo.meals(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun log(food: FoodEntity, grams: Double, mealType: MealType): Job = log(food, PortionChoice(grams, null, null), mealType)

    fun log(food: FoodEntity, choice: PortionChoice, mealType: MealType): Job = viewModelScope.launch {
        foods.log(date.value, food, choice, mealType)
    }

    fun toggleFavorite(food: FoodEntity) = viewModelScope.launch { repo.setFoodFavorite(food, !food.favorite) }

    fun saveCustom(food: FoodEntity) = viewModelScope.launch { repo.saveCustomFood(food) }

    fun delete(food: FoodEntity) = viewModelScope.launch { repo.deleteFood(food) }

    fun deletePortion(portion: FoodPortionEntity) = viewModelScope.launch { foods.deletePortion(portion) }

    // Refeições salvas

    fun saveMeal(draft: SavedMealDraft) = viewModelScope.launch {
        foods.saveMeal(
            SavedMealEntity(id = draft.id, name = draft.name.trim(), mealType = draft.type.name, createdMillis = draft.createdMillis),
            draft.items,
        )
    }

    fun saveDayAsMeal(type: MealType, name: String) = viewModelScope.launch { foods.saveDayAsMeal(date.value, type, name) }

    fun deleteSavedMeal(id: Long) = viewModelScope.launch { foods.deleteSavedMeal(id) }

    fun logSavedMeal(meal: SavedMeal, type: MealType) = viewModelScope.launch { foods.logSavedMeal(meal, date.value, type) }
}
