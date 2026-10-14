package com.addsoncosta.evolui.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profile WHERE id = 1")
    fun observe(): Flow<ProfileEntity?>

    @Query("SELECT * FROM profile WHERE id = 1")
    suspend fun get(): ProfileEntity?

    @Upsert
    suspend fun save(profile: ProfileEntity)

    @Query("DELETE FROM profile")
    suspend fun clear()
}

@Dao
interface DayLogDao {
    @Query("SELECT * FROM day_log WHERE epochDay = :day")
    fun observe(day: Long): Flow<DayLogEntity?>

    @Query("SELECT * FROM day_log WHERE epochDay = :day")
    suspend fun get(day: Long): DayLogEntity?

    /** Último peso registrado até a data (inclusive). */
    @Query("SELECT weightKg FROM day_log WHERE epochDay <= :day AND weightKg IS NOT NULL ORDER BY epochDay DESC LIMIT 1")
    fun observeLatestWeight(day: Long): Flow<Double?>

    @Query("SELECT * FROM day_log WHERE weightKg IS NOT NULL ORDER BY epochDay")
    fun observeWeights(): Flow<List<DayLogEntity>>

    @Query("SELECT * FROM day_log")
    suspend fun all(): List<DayLogEntity>

    @Upsert
    suspend fun save(log: DayLogEntity)

    @Upsert
    suspend fun saveAll(logs: List<DayLogEntity>)

    @Query("DELETE FROM day_log")
    suspend fun clear()
}

@Dao
interface FastDao {
    @Query("SELECT * FROM fast ORDER BY startMillis DESC")
    fun observeAll(): Flow<List<FastEntity>>

    @Query("SELECT * FROM fast")
    suspend fun all(): List<FastEntity>

    @Upsert
    suspend fun save(fast: FastEntity)

    @Insert
    suspend fun insertAll(fasts: List<FastEntity>)

    @Delete
    suspend fun delete(fast: FastEntity)

    @Query("DELETE FROM fast")
    suspend fun clear()
}

@Dao
interface PhotoDao {
    @Query("SELECT * FROM photo ORDER BY epochDay, id")
    fun observeAll(): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photo")
    suspend fun all(): List<PhotoEntity>

    @Insert
    suspend fun insert(photo: PhotoEntity)

    @Insert
    suspend fun insertAll(photos: List<PhotoEntity>)

    @Delete
    suspend fun delete(photo: PhotoEntity)

    @Query("DELETE FROM photo")
    suspend fun clear()

    @Query("SELECT * FROM photo WHERE id = :id")
    suspend fun get(id: Long): PhotoEntity?

    /** Insere e devolve o id da foto nova. */
    @Insert
    suspend fun insertReturningId(photo: PhotoEntity): Long

    @androidx.room.Update
    suspend fun update(photo: PhotoEntity)

    @Query("UPDATE photo SET scale = :scale, offsetX = :offsetX, offsetY = :offsetY WHERE id = :id")
    suspend fun setFraming(id: Long, scale: Double?, offsetX: Double?, offsetY: Double?)
}

@Dao
interface BadgeDao {
    @Query("SELECT * FROM badge ORDER BY earnedMillis DESC")
    fun observeAll(): Flow<List<BadgeEntity>>

    @Query("SELECT * FROM badge")
    suspend fun all(): List<BadgeEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(badges: List<BadgeEntity>)

    @Query("DELETE FROM badge")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM badge")
    fun observeCount(): Flow<Int>
}
