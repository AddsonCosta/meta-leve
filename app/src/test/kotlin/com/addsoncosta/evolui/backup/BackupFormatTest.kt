package com.addsoncosta.evolui.backup

import com.addsoncosta.evolui.data.Backup
import com.addsoncosta.evolui.data.PhotoEntity
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupFormatTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun oldBackupWithoutNewFieldsStillDecodes() {
        val old = """{"version":3,"profile":null,"days":[],"customFoods":[],"favoriteTacoIds":[],"meals":[],"customExercises":[],"workouts":[]}"""
        val b = json.decodeFromString(Backup.serializer(), old)
        assertTrue(b.includesFiles)
        assertTrue(b.omittedFiles.isEmpty())
        assertEquals(3, b.version)
    }

    @Test
    fun omittedFilesRoundTrip() {
        val day = LocalDate.of(2026, 9, 12).toEpochDay()
        val b = Backup(
            version = Backup.VERSION,
            profile = null,
            days = emptyList(),
            customFoods = emptyList(),
            favoriteTacoIds = emptyList(),
            meals = emptyList(),
            customExercises = emptyList(),
            workouts = emptyList(),
            includesFiles = false,
            omittedFiles = listOf(
                OmittedFile(kind = OmittedFile.KIND_PDF, folder = "anexos", fileName = "a.pdf", epochDay = day, recordId = 7, pages = 2),
                OmittedFile(kind = OmittedFile.KIND_PHOTO, folder = "fotos", fileName = "f.jpg", photo = PhotoEntity(id = 3, epochDay = day, fileName = "f.jpg")),
            ),
        )
        val back = json.decodeFromString(Backup.serializer(), json.encodeToString(Backup.serializer(), b))
        assertFalse(back.includesFiles)
        assertEquals(b.omittedFiles, back.omittedFiles)
    }

    @Test
    fun describesPdfAttachment() {
        val item = OmittedFile(
            kind = OmittedFile.KIND_PDF,
            fileName = "anexo_1.pdf",
            originalName = "hemograma.pdf",
            epochDay = LocalDate.of(2026, 9, 12).toEpochDay(),
            recordLabel = "Exame: Exame de sangue",
            pages = 2,
            valuesRead = 12,
            readText = "Hemoglobina 14,2",
        )
        assertEquals(
            "Exame: Exame de sangue, 12/09/2026, laudo PDF \"hemograma.pdf\", 2 páginas, 12 valores lidos guardados, texto lido guardado",
            item.description(),
        )
    }

    @Test
    fun describesPhoto() {
        val item = OmittedFile(kind = OmittedFile.KIND_PHOTO, fileName = "f.jpg", epochDay = LocalDate.of(2026, 10, 1).toEpochDay(), weightKg = 80.46)
        assertEquals("Foto de progresso, 01/10/2026, 80,5 kg", item.description())
        assertTrue(item.isPhoto)
    }

    @Test
    fun emailRules() {
        assertFalse(BackupRules.tooBigForEmail(24L * 1024 * 1024))
        assertTrue(BackupRules.tooBigForEmail(24L * 1024 * 1024 + 1))
        assertEquals("Backup do Evolui 20/10/2026", BackupRules.emailSubject(LocalDate.of(2026, 10, 20)))
        assertEquals("evolui-backup-sem-fotos-2026-10-20.zip", BackupRules.emailFileName(LocalDate.of(2026, 10, 20), includeFiles = false))
        val millis = LocalDate.of(2026, 10, 20).atTime(14, 5).toInstant(ZoneOffset.UTC).toEpochMilli()
        assertEquals("20/10/2026 às 14:05", BackupRules.formatMoment(millis, ZoneOffset.UTC))
    }
}
