package com.addsoncosta.evolui.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** Uma medida de pressão arterial (mmHg) feita em casa ou na farmácia. */
@Serializable
@Entity(tableName = "blood_pressure", indices = [Index("epochMillis")])
data class BloodPressureEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Data e hora da medida. */
    val epochMillis: Long,
    val systolic: Int,
    val diastolic: Int,
    /** Batimentos por minuto, quando o aparelho mostra. */
    val pulse: Int? = null,
    /** "ESQUERDO" ou "DIREITO". */
    val arm: String? = null,
    /** "SENTADO", "DEITADO" ou "EM_PE". */
    val position: String? = null,
    val notes: String? = null,
)
