package com.addsoncosta.evolui.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Uma atividade da rotina (ex.: "Lavar louça", 20 min, todos os dias). Entra sozinha no gasto
 * de cada dia marcado, a partir de [startEpochDay]. Nome e MET ficam guardados aqui para a rotina
 * continuar valendo mesmo se a atividade sair da lista.
 * [daysMask]: bit 0 = segunda ... bit 6 = domingo (ver core.RoutineCalc).
 */
@Serializable
@Entity(tableName = "routine_entry")
data class RoutineEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val exerciseId: Long?,
    val name: String,
    val met: Double,
    val minutes: Double,
    val daysMask: Int,
    val startEpochDay: Long,
)

/** A atividade [entryId] da rotina foi desmarcada no dia [epochDay] (não conta nesse dia). */
@Serializable
@Entity(tableName = "routine_skip", primaryKeys = ["entryId", "epochDay"], indices = [Index("epochDay")])
data class RoutineSkipEntity(
    val entryId: Long,
    val epochDay: Long,
)
