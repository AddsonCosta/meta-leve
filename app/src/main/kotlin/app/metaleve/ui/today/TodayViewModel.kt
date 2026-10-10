package app.metaleve.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.metaleve.AppContainer
import app.metaleve.core.ActivityLevel
import app.metaleve.core.DaySummary
import app.metaleve.core.EnergyCalculator
import app.metaleve.core.Profile
import app.metaleve.data.DayLogEntity
import app.metaleve.data.MealEntity
import app.metaleve.data.WorkoutEntity
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TodayState(
    val date: LocalDate,
    val profile: Profile,
    val log: DayLogEntity?,
    val weightKg: Double,
    val activity: ActivityLevel,
    val meals: List<MealEntity>,
    val workouts: List<WorkoutEntity>,
    val summary: DaySummary,
) {
    val eatenProtein get() = meals.sumOf { it.protein }
    val eatenCarb get() = meals.sumOf { it.carb }
    val eatenFat get() = meals.sumOf { it.fat }
}

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(private val container: AppContainer) : ViewModel() {
    private val repo = container.repository
    val date = container.selectedDate

    private data class DayData(val log: DayLogEntity?, val latestWeight: Double?, val meals: List<MealEntity>, val workouts: List<WorkoutEntity>)

    private val dayData = date.flatMapLatest { d ->
        combine(repo.dayLog(d), repo.latestWeight(d), repo.meals(d), repo.workouts(d)) { log, w, meals, workouts ->
            DayData(log, w, meals, workouts)
        }
    }

    val state: StateFlow<TodayState?> = combine(date, repo.profile.filterNotNull(), dayData) { d, profile, data ->
        val weight = data.latestWeight ?: profile.startWeightKg
        val activity = data.log?.activity?.let { ActivityLevel.valueOf(it) } ?: profile.defaultActivity
        TodayState(
            date = d,
            profile = profile,
            log = data.log,
            weightKg = weight,
            activity = activity,
            meals = data.meals,
            workouts = data.workouts,
            summary = EnergyCalculator.summary(profile, d, weight, activity, data.meals.sumOf { it.kcal }, data.workouts.sumOf { it.kcal }),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun shiftDay(days: Long) {
        val next = date.value.plusDays(days)
        if (!next.isAfter(LocalDate.now())) date.value = next
    }

    fun setWeight(kg: Double) = viewModelScope.launch { repo.updateDay(date.value) { it.copy(weightKg = kg) } }

    fun setActivity(level: ActivityLevel) = viewModelScope.launch { repo.updateDay(date.value) { it.copy(activity = level.name) } }

    fun setNote(note: String) = viewModelScope.launch { repo.updateDay(date.value) { it.copy(note = note.ifBlank { null }) } }

    fun deleteMeal(meal: MealEntity) = viewModelScope.launch { repo.deleteMeal(meal) }

    fun deleteWorkout(workout: WorkoutEntity) = viewModelScope.launch { repo.deleteWorkout(workout) }
}
