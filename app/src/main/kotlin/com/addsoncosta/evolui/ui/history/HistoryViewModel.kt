package com.addsoncosta.evolui.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.addsoncosta.evolui.AppContainer
import com.addsoncosta.evolui.core.Profile
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn

data class DayRow(val date: LocalDate, val eaten: Double, val exercise: Double, val weightKg: Double?)

data class HistoryState(
    val profile: Profile,
    val weights: List<Pair<LocalDate, Double>>,
    val days: List<DayRow>,
) {
    private val logged = days.filter { it.eaten > 0 }
    val avgEaten = if (logged.isEmpty()) 0.0 else logged.sumOf { it.eaten } / logged.size
    val avgExercise = if (days.isEmpty()) 0.0 else days.sumOf { it.exercise } / days.size
    val totalEaten = days.sumOf { it.eaten }
    val totalExercise = days.sumOf { it.exercise }
    val currentWeight = weights.lastOrNull()?.second ?: profile.startWeightKg
    val changeKg = currentWeight - profile.startWeightKg
}

class HistoryViewModel(container: AppContainer) : ViewModel() {
    private val repo = container.repository

    val state: StateFlow<HistoryState?> = combine(
        repo.profile.filterNotNull(),
        repo.weights(),
        repo.dailyEaten(),
        repo.dailyExercise(),
    ) { profile, weights, eaten, exercise ->
        val start = profile.startDate.toEpochDay()
        val eatenMap = eaten.associate { it.epochDay to it.total }
        val exerciseMap = exercise.associate { it.epochDay to it.total }
        val weightMap = weights.associate { it.epochDay to it.weightKg!! }
        val days = (eatenMap.keys + exerciseMap.keys + weightMap.keys)
            .filter { it >= start }
            .sortedDescending()
            .map { DayRow(LocalDate.ofEpochDay(it), eatenMap[it] ?: 0.0, exerciseMap[it] ?: 0.0, weightMap[it]) }
        HistoryState(
            profile = profile,
            weights = weights.filter { it.epochDay >= start }.map { LocalDate.ofEpochDay(it.epochDay) to it.weightKg!! },
            days = days,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
