package com.addsoncosta.evolui.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.addsoncosta.evolui.AppContainer
import com.addsoncosta.evolui.core.ActivityLevel
import com.addsoncosta.evolui.core.DaySummary
import com.addsoncosta.evolui.core.EnergyCalculator
import com.addsoncosta.evolui.core.Profile
import com.addsoncosta.evolui.data.DayLogEntity
import com.addsoncosta.evolui.data.MealEntity
import com.addsoncosta.evolui.data.WorkoutEntity
import com.addsoncosta.evolui.data.WorkoutRepository
import com.addsoncosta.evolui.health.HealthDay
import java.time.LocalDate
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
    private val workoutRepo = WorkoutRepository(container)
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

    /** Dados do Samsung Health (via Health Connect) do dia; nulo se não estiver conectado. */
    val health = MutableStateFlow<HealthDay?>(null)
    private val syncLock = Mutex()

    init {
        viewModelScope.launch { date.collect { syncHealth() } }
    }

    /**
     * Lê o dia no Health Connect (Samsung Health, Strava e outros apps), importa os treinos novos sem duplicar,
     * envia ao Health Connect os treinos do Evolui ainda não enviados e usa o peso, se o dia ainda não tiver um.
     * Tudo fora da thread principal; sem permissão ou sem Health Connect, só some o cartão.
     */
    fun syncHealth() = viewModelScope.launch {
        syncLock.withLock {
            val hc = container.health
            if (!hc.hasReadPermissions()) {
                health.value = null
                return@withLock
            }
            val d = date.value
            val profile = repo.profile.first()
            val weight = repo.latestWeight(d).first() ?: profile?.startWeightKg ?: 70.0
            val resting = profile?.let { EnergyCalculator.restingRate(it.sex, weight, it.heightCm, EnergyCalculator.ageOn(it.birthDate, d)) }
            val read: HealthDay? = try {
                hc.readDay(d, weight, resting)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            val day = read ?: return@withLock
            // O usuário pode ter trocado de dia enquanto lia.
            if (d == date.value) health.value = day
            workoutRepo.importFromHealth(day.workouts)
            workoutRepo.pushPending(d)
            val w = day.latestWeightKg
            if (w != null && w in 25.0..400.0 && repo.dayLogOnce(d)?.weightKg == null) repo.updateDay(d) { it.copy(weightKg = w) }
        }
    }

    fun shiftDay(days: Long) {
        val next = date.value.plusDays(days)
        if (!next.isAfter(LocalDate.now())) date.value = next
    }

    fun setWeight(kg: Double) = viewModelScope.launch { repo.updateDay(date.value) { it.copy(weightKg = kg) } }

    fun setActivity(level: ActivityLevel) = viewModelScope.launch { repo.updateDay(date.value) { it.copy(activity = level.name) } }

    fun setNote(note: String) = viewModelScope.launch { repo.updateDay(date.value) { it.copy(note = note.ifBlank { null }) } }

    fun deleteMeal(meal: MealEntity) = viewModelScope.launch { repo.deleteMeal(meal) }

    /** Apaga o treino; se veio de outro app não volta mais, e se o Evolui gravou no Health Connect, apaga lá também. */
    fun deleteWorkout(workout: WorkoutEntity) = viewModelScope.launch { workoutRepo.delete(workout) }
}
