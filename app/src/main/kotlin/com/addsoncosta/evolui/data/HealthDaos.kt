package com.addsoncosta.evolui.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface HealthRecordDao {
    @Query("SELECT * FROM health_record WHERE type = :type ORDER BY epochDay DESC, id DESC")
    fun observeByType(type: String): Flow<List<HealthRecordEntity>>

    @Query("SELECT * FROM health_record ORDER BY epochDay DESC, id DESC")
    fun observeAll(): Flow<List<HealthRecordEntity>>

    @Query("SELECT * FROM health_record WHERE id = :id")
    fun observe(id: Long): Flow<HealthRecordEntity?>

    @Query("SELECT * FROM health_record WHERE id = :id")
    suspend fun get(id: Long): HealthRecordEntity?

    @Query("SELECT * FROM health_record")
    suspend fun all(): List<HealthRecordEntity>

    @Query("SELECT * FROM health_record WHERE type = :type ORDER BY epochDay DESC, id DESC")
    suspend fun byType(type: String): List<HealthRecordEntity>

    /** Locais já usados (laboratórios, clínicas), para sugerir no formulário. */
    @Query("SELECT DISTINCT place FROM health_record WHERE type = :type AND place IS NOT NULL AND place != '' ORDER BY place")
    fun observePlaces(type: String): Flow<List<String>>

    /** Profissionais já usados, para sugerir no formulário. */
    @Query("SELECT DISTINCT professional FROM health_record WHERE type = :type AND professional IS NOT NULL AND professional != '' ORDER BY professional")
    fun observeProfessionals(type: String): Flow<List<String>>

    @Query("SELECT DISTINCT place FROM health_record WHERE type = :type AND place IS NOT NULL AND place != ''")
    suspend fun places(type: String): List<String>

    @Upsert
    suspend fun save(record: HealthRecordEntity): Long

    @Insert
    suspend fun insertAll(records: List<HealthRecordEntity>)

    @Delete
    suspend fun delete(record: HealthRecordEntity)

    @Query("DELETE FROM health_record")
    suspend fun clear()
}

@Dao
interface HealthAttachmentDao {
    @Query("SELECT * FROM health_attachment WHERE recordId = :recordId ORDER BY addedMillis")
    fun observeFor(recordId: Long): Flow<List<HealthAttachmentEntity>>

    @Query("SELECT * FROM health_attachment WHERE recordId = :recordId ORDER BY addedMillis")
    suspend fun forRecord(recordId: Long): List<HealthAttachmentEntity>

    @Query("SELECT * FROM health_attachment")
    suspend fun all(): List<HealthAttachmentEntity>

    @Query("SELECT * FROM health_attachment")
    fun observeAll(): Flow<List<HealthAttachmentEntity>>

    @Query("DELETE FROM health_attachment WHERE recordId = :recordId")
    suspend fun deleteFor(recordId: Long)

    @Upsert
    suspend fun save(attachment: HealthAttachmentEntity): Long

    @Insert
    suspend fun insertAll(attachments: List<HealthAttachmentEntity>)

    @Delete
    suspend fun delete(attachment: HealthAttachmentEntity)

    @Query("DELETE FROM health_attachment")
    suspend fun clear()
}

@Dao
interface LabResultDao {
    @Query("SELECT * FROM lab_result WHERE recordId = :recordId ORDER BY id")
    fun observeFor(recordId: Long): Flow<List<LabResultEntity>>

    @Query("SELECT * FROM lab_result WHERE analyte = :analyte ORDER BY epochDay")
    fun observeAnalyte(analyte: String): Flow<List<LabResultEntity>>

    @Query("SELECT * FROM lab_result ORDER BY epochDay")
    fun observeAll(): Flow<List<LabResultEntity>>

    @Query("SELECT * FROM lab_result")
    suspend fun all(): List<LabResultEntity>

    @Query("DELETE FROM lab_result WHERE recordId = :recordId")
    suspend fun deleteFor(recordId: Long)

    @Query("SELECT * FROM lab_result WHERE recordId = :recordId ORDER BY id")
    suspend fun forRecord(recordId: Long): List<LabResultEntity>

    /** Mantém a data dos valores igual à do exame. */
    @Query("UPDATE lab_result SET epochDay = :epochDay WHERE recordId = :recordId")
    suspend fun moveTo(recordId: Long, epochDay: Long)

    @Insert
    suspend fun insertAll(results: List<LabResultEntity>)

    @Query("DELETE FROM lab_result")
    suspend fun clear()
}
