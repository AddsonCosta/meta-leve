package com.addsoncosta.evolui.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface HealthEventDao {
    @Query("SELECT * FROM health_event WHERE epochDay BETWEEN :from AND :to ORDER BY epochDay, minuteOfDay")
    fun observeRange(from: Long, to: Long): Flow<List<HealthEventEntity>>

    @Query("SELECT * FROM health_event ORDER BY epochDay, minuteOfDay")
    fun observeAll(): Flow<List<HealthEventEntity>>

    @Query("SELECT * FROM health_event WHERE id = :id")
    suspend fun get(id: Long): HealthEventEntity?

    @Query("SELECT * FROM health_event")
    suspend fun all(): List<HealthEventEntity>

    @Upsert
    suspend fun save(event: HealthEventEntity): Long

    @Insert
    suspend fun insertAll(events: List<HealthEventEntity>)

    @Delete
    suspend fun delete(event: HealthEventEntity)

    @Query("DELETE FROM health_event")
    suspend fun clear()
}

@Dao
interface HealthConditionDao {
    @Query("SELECT * FROM health_condition ORDER BY status, name")
    fun observeAll(): Flow<List<HealthConditionEntity>>

    @Query("SELECT * FROM health_condition")
    suspend fun all(): List<HealthConditionEntity>

    @Upsert
    suspend fun save(condition: HealthConditionEntity): Long

    @Insert
    suspend fun insertAll(conditions: List<HealthConditionEntity>)

    @Delete
    suspend fun delete(condition: HealthConditionEntity)

    @Query("DELETE FROM health_condition")
    suspend fun clear()
}

@Dao
interface ToothStateDao {
    @Query("SELECT * FROM tooth_state")
    fun observeAll(): Flow<List<ToothStateEntity>>

    @Query("SELECT * FROM tooth_state")
    suspend fun all(): List<ToothStateEntity>

    @Upsert
    suspend fun save(tooth: ToothStateEntity)

    @Upsert
    suspend fun saveAll(teeth: List<ToothStateEntity>)

    @Delete
    suspend fun delete(tooth: ToothStateEntity)

    @Query("DELETE FROM tooth_state")
    suspend fun clear()
}

@Dao
interface MenstrualCycleDao {
    @Query("SELECT * FROM menstrual_cycle ORDER BY startEpochDay DESC")
    fun observeAll(): Flow<List<MenstrualCycleEntity>>

    @Query("SELECT * FROM menstrual_cycle")
    suspend fun all(): List<MenstrualCycleEntity>

    @Upsert
    suspend fun save(cycle: MenstrualCycleEntity): Long

    @Insert
    suspend fun insertAll(cycles: List<MenstrualCycleEntity>)

    @Delete
    suspend fun delete(cycle: MenstrualCycleEntity)

    @Query("DELETE FROM menstrual_cycle")
    suspend fun clear()
}
