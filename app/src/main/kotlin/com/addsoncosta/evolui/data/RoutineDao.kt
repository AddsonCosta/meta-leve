package com.addsoncosta.evolui.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface RoutineDao {
    @Query("SELECT * FROM routine_entry ORDER BY name, id")
    fun observeEntries(): Flow<List<RoutineEntryEntity>>

    @Query("SELECT * FROM routine_entry")
    suspend fun all(): List<RoutineEntryEntity>

    @Upsert
    suspend fun save(entry: RoutineEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<RoutineEntryEntity>)

    @Query("DELETE FROM routine_entry WHERE id = :id")
    suspend fun deleteEntry(id: Long)

    @Query("DELETE FROM routine_entry")
    suspend fun clear()

    @Query("SELECT * FROM routine_skip WHERE epochDay = :day")
    fun observeSkips(day: Long): Flow<List<RoutineSkipEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun skip(skip: RoutineSkipEntity)

    @Query("DELETE FROM routine_skip WHERE entryId = :entryId AND epochDay = :day")
    suspend fun unskip(entryId: Long, day: Long)

    @Query("DELETE FROM routine_skip WHERE entryId = :entryId")
    suspend fun clearSkips(entryId: Long)

    @Query("DELETE FROM routine_skip")
    suspend fun clearAllSkips()
}
