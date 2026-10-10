package app.metaleve.ui.food

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.metaleve.AppContainer
import app.metaleve.data.FoodEntity
import app.metaleve.data.MealType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class FoodViewModel(private val container: AppContainer) : ViewModel() {
    private val repo = container.repository
    val date = container.selectedDate
    val query = MutableStateFlow("")

    val results: StateFlow<List<FoodEntity>> = query
        .debounce(200)
        .flatMapLatest { repo.searchFoods(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun log(food: FoodEntity, grams: Double, mealType: MealType) = viewModelScope.launch {
        repo.logFood(date.value, food, grams, mealType)
    }

    fun toggleFavorite(food: FoodEntity) = viewModelScope.launch { repo.setFoodFavorite(food, !food.favorite) }

    fun saveCustom(food: FoodEntity) = viewModelScope.launch { repo.saveCustomFood(food) }

    fun delete(food: FoodEntity) = viewModelScope.launch { repo.deleteFood(food) }
}
