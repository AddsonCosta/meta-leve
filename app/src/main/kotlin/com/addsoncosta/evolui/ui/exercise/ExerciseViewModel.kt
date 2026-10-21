package com.addsoncosta.evolui.ui.exercise

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.addsoncosta.evolui.AppContainer
import com.addsoncosta.evolui.core.ActivityLevel
import com.addsoncosta.evolui.core.EnergyCalculator
import com.addsoncosta.evolui.core.Profile
import com.addsoncosta.evolui.core.RoutineCalc
import com.addsoncosta.evolui.data.ExerciseEntity
import com.addsoncosta.evolui.data.Repository
import com.addsoncosta.evolui.data.RoutineEntryEntity
import com.addsoncosta.evolui.data.RoutineRepository
import com.addsoncosta.evolui.data.WorkoutEntity
import com.addsoncosta.evolui.data.WorkoutRepository
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Dados da pessoa usados no gasto do exercício. Sem perfil, cai na fórmula clássica pelo peso. */
data class PersonInfo(
    val weightKg: Double,
    val heightCm: Double? = null,
    val age: Int? = null,
    val restingKcalPerDay: Double? = null,
) {
    /** kcal estimadas para um MET e uma duração. */
    fun kcal(met: Double, minutes: Double): Double {
        if (minutes <= 0) return 0.0
        val resting = restingKcalPerDay
        return if (resting != null && resting > 0) {
            EnergyCalculator.personalExerciseKcal(met, minutes, resting)
        } else {
            EnergyCalculator.exerciseKcal(met, weightKg, minutes)
        }
    }

    /** kcal acima do repouso (MET − 1), para a rotina do dia a dia: o repouso já entra no gasto do dia. */
    fun netKcal(met: Double, minutes: Double): Double {
        if (minutes <= 0) return 0.0
        val resting = restingKcalPerDay
        return if (resting != null && resting > 0) {
            RoutineCalc.netKcal(met, minutes, resting)
        } else {
            RoutineCalc.netKcalByWeight(met, weightKg, minutes)
        }
    }

    companion object {
        fun from(profile: Profile?, weightKg: Double, date: LocalDate): PersonInfo {
            if (profile == null) return PersonInfo(weightKg)
            val age = EnergyCalculator.ageOn(profile.birthDate, date)
            return PersonInfo(
                weightKg = weightKg,
                heightCm = profile.heightCm,
                age = age,
                restingKcalPerDay = EnergyCalculator.restingRate(profile.sex, weightKg, profile.heightCm, age),
            )
        }
    }
}

/** Atividades de um dia, para o histórico. */
data class DayGroup(val date: LocalDate, val workouts: List<WorkoutEntity>) {
    val minutes: Double get() = workouts.sumOf { it.minutes }
    val kcal: Double get() = workouts.sumOf { it.kcal }
}

/** Resumo da semana (segunda a domingo). A OMS recomenda 150 min de atividade moderada por semana. */
data class WeekSummary(val start: LocalDate, val end: LocalDate, val minutes: Double, val kcal: Double, val count: Int) {
    val progress: Float get() = (minutes / WHO_WEEKLY_MINUTES).toFloat().coerceIn(0f, 1f)

    companion object {
        const val WHO_WEEKLY_MINUTES = 150.0
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ExerciseViewModel(private val container: AppContainer) : ViewModel() {
    private val repo = container.repository
    private val workoutRepo = WorkoutRepository(container)
    private val routineRepo = RoutineRepository(container.database)
    val date = container.selectedDate

    /** Atividades da rotina do dia a dia. */
    val routine: StateFlow<List<RoutineEntryEntity>> = routineRepo.entries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Lista completa (sem filtro), para escolher a atividade da rotina. */
    val allExercises: StateFlow<List<ExerciseEntity>> = repo.exercises()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Nível de atividade padrão do perfil (para avisar sobre contagem dupla com a rotina). */
    val defaultActivity: StateFlow<ActivityLevel?> = repo.profile.map { it?.defaultActivity }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Texto da busca e categoria escolhida ([FAVORITES] = só favoritos; nulo = todas). */
    val query = MutableStateFlow("")
    val category = MutableStateFlow<String?>(null)

    private class Searchable(val exercise: ExerciseEntity, val key: String, val nameKey: String)

    private val catalog = repo.exercises()
        .map { list -> list.map { Searchable(it, Repository.normalize(it.name + " " + it.category), Repository.normalize(it.name)) } }
        .flowOn(Dispatchers.Default)

    /** Categorias na ordem em que aparecem (o catálogo já vem ordenado). */
    val categories: StateFlow<List<String>> = repo.exercises()
        .map { list -> list.map { it.category }.distinct().sorted() }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Exercícios filtrados pela busca (sem acento) e pela categoria; favoritos no topo. */
    val exercises: StateFlow<List<ExerciseEntity>> = combine(catalog, query, category) { list, q, cat ->
        val words = Repository.normalize(q).split(' ').filter { it.isNotBlank() }
        val first = words.firstOrNull()
        list.asSequence()
            .filter { s ->
                when (cat) {
                    null -> true
                    FAVORITES -> s.exercise.favorite
                    else -> s.exercise.category == cat
                }
            }
            .filter { s -> words.all { it in s.key } }
            .toList()
            .sortedWith(
                compareByDescending<Searchable> { it.exercise.favorite }
                    .thenByDescending { first != null && it.nameKey.startsWith(first) }
            )
            .map { it.exercise }
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Peso, altura e idade usados no cálculo (último peso registrado até o dia selecionado). */
    val person: StateFlow<PersonInfo> = date.flatMapLatest { d ->
        combine(repo.latestWeight(d), repo.profile) { w, p -> PersonInfo.from(p, w ?: p?.startWeightKg ?: 70.0, d) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PersonInfo(70.0))

    /** Histórico agrupado por dia, mais recentes primeiro. */
    val history: StateFlow<List<DayGroup>> = workoutRepo.history()
        .map { list -> list.groupBy { it.epochDay }.map { (day, items) -> DayGroup(LocalDate.ofEpochDay(day), items) } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Minutos e calorias da semana atual. */
    val week: StateFlow<WeekSummary> = history.map { groups -> weekOf(groups, LocalDate.now()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), weekOf(emptyList(), LocalDate.now()))

    /** Health Connect com permissão de leitura. */
    val healthConnected = MutableStateFlow(false)
    val syncing = MutableStateFlow(false)
    val syncMessage = MutableStateFlow<String?>(null)
    private var autoSynced = false

    init {
        viewModelScope.launch { healthConnected.value = container.health.hasReadPermissions() }
    }

    fun setQuery(text: String) {
        query.value = text
    }

    fun setCategory(value: String?) {
        category.value = value
    }

    fun log(exercise: ExerciseEntity, minutes: Double, startTime: LocalTime?) = viewModelScope.launch {
        val d = date.value
        workoutRepo.log(d, exercise, minutes, currentPerson(d).kcal(exercise.met, minutes), startTime)
    }

    /** Cria ou altera uma atividade da rotina; as novas passam a contar a partir de hoje. */
    fun saveRoutine(entry: RoutineEntryEntity) = viewModelScope.launch { routineRepo.save(entry) }

    fun deleteRoutine(entry: RoutineEntryEntity) = viewModelScope.launch { routineRepo.delete(entry) }

    fun toggleFavorite(exercise: ExerciseEntity) = viewModelScope.launch { repo.setExerciseFavorite(exercise, !exercise.favorite) }

    fun saveCustom(name: String, met: Double) = viewModelScope.launch {
        repo.saveCustomExercise(ExerciseEntity(category = CUSTOM_CATEGORY, name = name, met = met))
    }

    fun delete(exercise: ExerciseEntity) = viewModelScope.launch { repo.deleteExercise(exercise) }

    fun updateMinutes(workout: WorkoutEntity, minutes: Double) = viewModelScope.launch { workoutRepo.updateMinutes(workout, minutes) }

    fun deleteWorkout(workout: WorkoutEntity) = viewModelScope.launch { workoutRepo.delete(workout) }

    /** Na primeira vez que o histórico abre, busca os treinos da semana nos outros apps. */
    fun onHistoryOpened() {
        if (autoSynced) return
        autoSynced = true
        syncWeek(quiet = true)
    }

    /** Traz do Health Connect os treinos dos últimos 7 dias (Samsung Health, Strava...). */
    fun syncWeek(quiet: Boolean = false) = viewModelScope.launch {
        if (syncing.value) return@launch
        syncing.value = true
        try {
            val hc = container.health
            val connected = hc.hasReadPermissions()
            healthConnected.value = connected
            if (!connected) return@launch
            val today = LocalDate.now()
            val p = currentPerson(today)
            val found = hc.readWorkouts(today.minusDays(6), today, p.weightKg, p.restingKcalPerDay)
            val added = workoutRepo.importFromHealth(found)
            if (!quiet || added > 0) {
                syncMessage.value = when (added) {
                    0 -> "Tudo em dia: nenhum treino novo nos últimos 7 dias."
                    1 -> "1 treino novo trazido dos outros apps."
                    else -> "$added treinos novos trazidos dos outros apps."
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!quiet) syncMessage.value = "Não foi possível ler o Health Connect agora. Tente de novo mais tarde."
        } finally {
            syncing.value = false
        }
    }

    private suspend fun currentPerson(d: LocalDate): PersonInfo {
        val profile = repo.profile.first()
        val weight = repo.latestWeight(d).first() ?: profile?.startWeightKg ?: 70.0
        return PersonInfo.from(profile, weight, d)
    }

    private fun weekOf(groups: List<DayGroup>, today: LocalDate): WeekSummary {
        val start = today.with(DayOfWeek.MONDAY)
        val end = start.plusDays(6)
        val inWeek = groups.filter { !it.date.isBefore(start) && !it.date.isAfter(end) }.flatMap { it.workouts }
        return WeekSummary(start, end, inWeek.sumOf { it.minutes }, inWeek.sumOf { it.kcal }, inWeek.size)
    }

    companion object {
        const val FAVORITES = "__favoritos__"
        const val CUSTOM_CATEGORY = "Meus exercícios"
    }
}
