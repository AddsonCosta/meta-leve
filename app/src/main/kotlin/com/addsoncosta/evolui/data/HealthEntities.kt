package com.addsoncosta.evolui.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** Áreas da seção Saúde. O [name] é gravado no banco; não renomear. */
enum class HealthType(val label: String, val singular: String) {
    CONSULTA("Consultas", "Consulta"),
    EXAME("Exames", "Exame"),
    MEDICACAO("Medicação", "Medicação"),
    ODONTOLOGIA("Odontologia", "Atendimento odontológico"),
    NUTRICAO("Nutrição", "Consulta de nutrição"),
    FISIOTERAPIA("Fisioterapia", "Sessão de fisioterapia"),
    TERAPIA("Terapia", "Sessão de terapia"),
    TRATAMENTO("Tratamentos", "Tratamento"),
    BIOIMPEDANCIA("Bioimpedância", "Bioimpedância"),
    ANAMNESE("Anamnese", "Anamnese"),
    OFTALMOLOGIA("Oftalmologia", "Consulta oftalmológica"),
    SUPLEMENTACAO("Suplementação", "Suplemento"),
}

/**
 * Um registro da seção Saúde (consulta, exame, medicação...).
 * Campos específicos de cada tipo ficam em [extra], um JSON simples (mapa de texto para texto).
 */
@Serializable
@Entity(tableName = "health_record", indices = [Index("type"), Index("epochDay")])
data class HealthRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val epochDay: Long,
    val title: String,
    /** Médico, dentista ou profissional. */
    val professional: String? = null,
    /** Laboratório, clínica ou local. */
    val place: String? = null,
    val description: String? = null,
    /** Ex.: "Em andamento", "Concluído", "Suspenso". */
    val status: String? = null,
    val extra: String? = null,
    val createdMillis: Long,
    val updatedMillis: Long,
)

/** Arquivo (PDF ou imagem) anexado a um registro, guardado na pasta interna do app. */
@Serializable
@Entity(tableName = "health_attachment", indices = [Index("recordId")])
data class HealthAttachmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recordId: Long,
    val fileName: String,
    val mimeType: String,
    val originalName: String? = null,
    /** Texto lido do arquivo no próprio celular, para busca e preenchimento. */
    val ocrText: String? = null,
    val addedMillis: Long,
)

/** Um valor de exame (hemograma, bioimpedância...), para comparar entre datas. */
@Serializable
@Entity(tableName = "lab_result", indices = [Index("recordId"), Index("analyte")])
data class LabResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recordId: Long,
    val epochDay: Long,
    /** Nome padronizado do item (ex.: "Hemoglobina"). */
    val analyte: String,
    val value: Double,
    val unit: String? = null,
    val refLow: Double? = null,
    val refHigh: Double? = null,
    val refText: String? = null,
)

/** Evento no calendário de saúde (consulta marcada, exame, horário de remédio...). */
@Serializable
@Entity(tableName = "health_event", indices = [Index("epochDay")])
data class HealthEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Um [HealthType.name]. */
    val type: String,
    val epochDay: Long,
    /** Minutos desde a meia-noite; nulo = dia inteiro. */
    val minuteOfDay: Int? = null,
    val title: String,
    val place: String? = null,
    val notes: String? = null,
    /** Lembrete: quantos minutos antes avisar; nulo = sem lembrete. */
    val remindMinutesBefore: Int? = null,
    val done: Boolean = false,
    val recordId: Long? = null,
)

/** Doença ou condição da anamnese. */
@Serializable
@Entity(tableName = "health_condition")
data class HealthConditionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** "PRE_EXISTENTE", "ATUAL" ou "TRATADA". */
    val status: String,
    val sinceText: String? = null,
    val notes: String? = null,
)

/** Situação de um dente, pela numeração FDI (11 a 48; 51 a 85 para dentes de leite). */
@Serializable
@Entity(tableName = "tooth_state")
data class ToothStateEntity(
    @PrimaryKey val number: Int,
    val status: String,
    val notes: String? = null,
    val updatedEpochDay: Long? = null,
)

/** Ciclo menstrual: um registro por menstruação. */
@Serializable
@Entity(tableName = "menstrual_cycle", indices = [Index("startEpochDay")])
data class MenstrualCycleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startEpochDay: Long,
    val endEpochDay: Long? = null,
    /** "LEVE", "MEDIO" ou "INTENSO". */
    val flow: String? = null,
    /** Sintomas separados por vírgula. */
    val symptoms: String? = null,
    val notes: String? = null,
)
