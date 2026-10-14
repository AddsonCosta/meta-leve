package com.addsoncosta.evolui.ui.exercise

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.addsoncosta.evolui.AppContainer
import com.addsoncosta.evolui.core.EnergyCalculator
import com.addsoncosta.evolui.data.ExerciseEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class ExerciseViewModel(private val container: AppContainer) : ViewModel() {
    private val repo = container.repository
    val date = container.selectedDate

    val exercises: StateFlow<List<ExerciseEntity>> = repo.exercises()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Peso usado no cálculo: o último registrado até o dia selecionado. */
    val weightKg: StateFlow<Double> = date.flatMapLatest { d ->
        combine(repo.latestWeight(d), repo.profile) { w, p -> w ?: p?.startWeightKg ?: 70.0 }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 70.0)

    fun kcal(exercise: ExerciseEntity, minutes: Double) = EnergyCalculator.exerciseKcal(exercise.met, weightKg.value, minutes)

    fun log(exercise: ExerciseEntity, minutes: Double) = viewModelScope.launch {
        repo.logWorkout(date.value, exercise, minutes, kcal(exercise, minutes))
    }

    fun toggleFavorite(exercise: ExerciseEntity) = viewModelScope.launch { repo.setExerciseFavorite(exercise, !exercise.favorite) }

    fun saveCustom(name: String, met: Double) = viewModelScope.launch {
        repo.saveCustomExercise(ExerciseEntity(category = "Meus exercícios", name = name, met = met))
    }

    fun delete(exercise: ExerciseEntity) = viewModelScope.launch { repo.deleteExercise(exercise) }
}
