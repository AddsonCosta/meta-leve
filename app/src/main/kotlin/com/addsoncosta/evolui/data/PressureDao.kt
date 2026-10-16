package com.addsoncosta.evolui.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface BloodPressureDao {
    /** Da mais recente para a mais antiga. */
    @Query("SELECT * FROM blood_pressure ORDER BY epochMillis DESC")
    fun observeAll(): Flow<List<BloodPressureEntity>>

    @Query("SELECT * FROM blood_pressure ORDER BY epochMillis DESC LIMIT 1")
    fun observeLatest(): Flow<BloodPressureEntity?>

    @Query("SELECT * FROM blood_pressure ORDER BY epochMillis")
    suspend fun all(): List<BloodPressureEntity>

    @Upsert
    suspend fun save(reading: BloodPressureEntity): Long

    @Insert
    suspend fun insertAll(readings: List<BloodPressureEntity>)

    @Delete
    suspend fun delete(reading: BloodPressureEntity)

    @Query("DELETE FROM blood_pressure")
    suspend fun clear()
}
