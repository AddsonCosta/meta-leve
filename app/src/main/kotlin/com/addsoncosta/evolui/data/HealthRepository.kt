package com.addsoncosta.evolui.data

import android.content.Context
import androidx.room.withTransaction
import com.addsoncosta.evolui.health.Attachments
import com.addsoncosta.evolui.health.PendingFile
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** Registros, anexos e valores de exames da seção Saúde. Tudo fica só no celular. */
class HealthRepository(context: Context, private val db: AppDatabase) {
    private val app = context.applicationContext
    private val records = db.healthRecordDao()
    private val attachments = db.healthAttachmentDao()
    private val labs = db.labResultDao()

    fun observeType(type: HealthType): Flow<List<HealthRecordEntity>> = records.observeByType(type.name)
    fun observeAll(): Flow<List<HealthRecordEntity>> = records.observeAll()
    fun observeRecord(id: Long): Flow<HealthRecordEntity?> = records.observe(id)
    fun observeAttachments(recordId: Long): Flow<List<HealthAttachmentEntity>> = attachments.observeFor(recordId)
    fun observeAllAttachments(): Flow<List<HealthAttachmentEntity>> = attachments.observeAll()
    fun observeResults(recordId: Long): Flow<List<LabResultEntity>> = labs.observeFor(recordId)
    fun observeAllResults(): Flow<List<LabResultEntity>> = labs.observeAll()
    fun observePlaces(type: HealthType): Flow<List<String>> = records.observePlaces(type.name)
    fun observeProfessionals(type: HealthType): Flow<List<String>> = records.observeProfessionals(type.name)

    suspend fun get(id: Long): HealthRecordEntity? = records.get(id)
    suspend fun results(recordId: Long): List<LabResultEntity> = labs.forRecord(recordId)
    suspend fun places(type: HealthType): List<String> = records.places(type.name)

    /**
     * Salva o registro. [results] (quando não nulo) substitui os valores do exame;
     * [newFiles] são anexos escolhidos no formulário. Devolve o id do registro.
     */
    suspend fun save(
        record: HealthRecordEntity,
        results: List<LabResultEntity>? = null,
        newFiles: List<PendingFile> = emptyList(),
    ): Long = db.withTransaction {
        val now = System.currentTimeMillis()
        val id = if (record.id == 0L) {
            records.save(record.copy(createdMillis = now, updatedMillis = now))
        } else {
            records.save(record.copy(updatedMillis = now))
            record.id
        }
        if (results != null) {
            labs.deleteFor(id)
            labs.insertAll(results.map { it.copy(id = 0, recordId = id, epochDay = record.epochDay) })
        } else {
            labs.moveTo(id, record.epochDay)
        }
        newFiles.forEach { addAttachment(id, it) }
        id
    }

    suspend fun addAttachment(recordId: Long, file: PendingFile): Long = attachments.save(
        HealthAttachmentEntity(
            recordId = recordId,
            fileName = file.fileName,
            mimeType = file.mimeType,
            originalName = file.originalName,
            ocrText = file.ocrText,
            addedMillis = System.currentTimeMillis(),
        ),
    )

    suspend fun removeAttachment(a: HealthAttachmentEntity) {
        attachments.delete(a)
        Attachments.deleteFile(app, a.fileName)
    }

    /** Apaga o registro com seus anexos (inclusive os arquivos) e valores. */
    suspend fun delete(record: HealthRecordEntity) {
        val files = attachments.forRecord(record.id)
        db.withTransaction {
            attachments.deleteFor(record.id)
            labs.deleteFor(record.id)
            records.delete(record)
        }
        withContext(Dispatchers.IO) { files.forEach { Attachments.file(app, it.fileName).delete() } }
    }

    /** Junta valores lidos de um arquivo aos do exame: o mesmo item é substituído. */
    suspend fun mergeResults(record: HealthRecordEntity, newOnes: List<LabResultEntity>) {
        val current = labs.forRecord(record.id)
        val names = newOnes.map { it.analyte.lowercase() }.toSet()
        val merged = current.filter { it.analyte.lowercase() !in names } + newOnes
        db.withTransaction {
            labs.deleteFor(record.id)
            labs.insertAll(merged.map { it.copy(id = 0, recordId = record.id, epochDay = record.epochDay) })
        }
    }

    suspend fun update(record: HealthRecordEntity) {
        records.save(record.copy(updatedMillis = System.currentTimeMillis()))
        labs.moveTo(record.id, record.epochDay)
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        private val mapSerializer = MapSerializer(String.serializer(), String.serializer())

        fun decodeExtra(extra: String?): Map<String, String> =
            if (extra.isNullOrBlank()) emptyMap() else runCatching { json.decodeFromString(mapSerializer, extra) }.getOrDefault(emptyMap())

        fun encodeExtra(map: Map<String, String>): String? {
            val clean = map.filterValues { it.isNotBlank() }.mapValues { it.value.trim() }
            return if (clean.isEmpty()) null else json.encodeToString(mapSerializer, clean)
        }

        /** Datas dos campos extras ficam como "2026-05-14". */
        fun parseDate(text: String?): LocalDate? = text?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    }
}
