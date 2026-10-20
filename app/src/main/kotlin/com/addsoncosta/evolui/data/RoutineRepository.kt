package com.addsoncosta.evolui.data

import com.addsoncosta.evolui.core.RoutineCalc
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Uma atividade da rotina num dia: [skipped] quando o usuário desmarcou só nesse dia. */
data class RoutineDayEntry(val entry: RoutineEntryEntity, val skipped: Boolean)

/** Rotina do dia a dia: o usuário monta uma vez e ela entra sozinha no gasto de cada dia marcado. */
class RoutineRepository(private val db: AppDatabase) {
    private val dao get() = db.routineDao()

    fun entries(): Flow<List<RoutineEntryEntity>> = dao.observeEntries()

    /** Atividades da rotina que valem em [date] (dia da semana marcado e já criadas), com o que foi desmarcado. */
    fun day(date: LocalDate): Flow<List<RoutineDayEntry>> {
        val day = date.toEpochDay()
        return combine(dao.observeEntries(), dao.observeSkips(day)) { entries, skips ->
            val skipped = skips.map { it.entryId }.toSet()
            entries
                .filter { it.startEpochDay <= day && RoutineCalc.appliesOn(it.daysMask, date) }
                .map { RoutineDayEntry(it, it.id in skipped) }
        }
    }

    /** Cria ou altera uma atividade da rotina. */
    suspend fun save(entry: RoutineEntryEntity) = dao.save(entry)

    suspend fun delete(entry: RoutineEntryEntity) {
        dao.clearSkips(entry.id)
        dao.deleteEntry(entry.id)
    }

    /** Marca ou desmarca a atividade só no dia [date]. */
    suspend fun setSkipped(entry: RoutineEntryEntity, date: LocalDate, skipped: Boolean) {
        val day = date.toEpochDay()
        if (skipped) dao.skip(RoutineSkipEntity(entry.id, day)) else dao.unskip(entry.id, day)
    }
}
