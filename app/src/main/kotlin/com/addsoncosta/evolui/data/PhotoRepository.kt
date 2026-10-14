package com.addsoncosta.evolui.data

import android.content.Context
import android.net.Uri
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Fotos de progresso: troca de arquivo, enquadramento, legenda e o par antes/depois escolhido.
 * Usa a mesma pasta interna do Repository ("fotos").
 */
class PhotoRepository(context: Context, private val db: AppDatabase) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("antes_depois", Context.MODE_PRIVATE)

    private val _pair = MutableStateFlow(readPair())
    /** Ids escolhidos para Antes e Depois (nulo = automático: a mais antiga e a mais recente). */
    val pair: StateFlow<Pair<Long?, Long?>> = _pair.asStateFlow()

    fun file(photo: PhotoEntity): File = File(dir(), photo.fileName)

    /** Copia a foto da galeria para a pasta do app e devolve o id novo. */
    suspend fun add(uri: Uri, date: LocalDate, weightKg: Double?): Long = withContext(Dispatchers.IO) {
        val name = copy(uri)
        db.photoDao().insertReturningId(PhotoEntity(epochDay = date.toEpochDay(), fileName = name, weightKg = weightKg))
    }

    /** Troca o arquivo da foto mantendo data, peso e legenda; o enquadramento volta ao centro. */
    suspend fun replace(photo: PhotoEntity, uri: Uri) = withContext(Dispatchers.IO) {
        val name = copy(uri)
        db.photoDao().update(photo.copy(fileName = name, scale = null, offsetX = null, offsetY = null))
        File(dir(), photo.fileName).delete()
    }

    suspend fun update(photo: PhotoEntity) = withContext(Dispatchers.IO) { db.photoDao().update(photo) }

    suspend fun saveFraming(photoId: Long, scale: Double, offsetX: Double, offsetY: Double) = withContext(Dispatchers.IO) {
        db.photoDao().setFraming(photoId, scale, offsetX, offsetY)
    }

    suspend fun delete(photo: PhotoEntity) = withContext(Dispatchers.IO) {
        File(dir(), photo.fileName).delete()
        db.photoDao().delete(photo)
        val (b, a) = _pair.value
        if (b == photo.id) setBefore(null)
        if (a == photo.id) setAfter(null)
    }

    fun setBefore(id: Long?) = savePair(id, _pair.value.second)

    fun setAfter(id: Long?) = savePair(_pair.value.first, id)

    private fun savePair(before: Long?, after: Long?) {
        prefs.edit().putLong(KEY_BEFORE, before ?: -1L).putLong(KEY_AFTER, after ?: -1L).apply()
        _pair.value = before to after
    }

    private fun readPair(): Pair<Long?, Long?> =
        prefs.getLong(KEY_BEFORE, -1L).takeIf { it >= 0 } to prefs.getLong(KEY_AFTER, -1L).takeIf { it >= 0 }

    private fun copy(uri: Uri): String {
        val name = "foto_${System.currentTimeMillis()}.jpg"
        val input = app.contentResolver.openInputStream(uri) ?: error("Não foi possível abrir a foto")
        input.use { i -> File(dir(), name).outputStream().use { i.copyTo(it) } }
        return name
    }

    private fun dir() = File(app.filesDir, "fotos").apply { mkdirs() }

    private companion object {
        const val KEY_BEFORE = "antes"
        const val KEY_AFTER = "depois"
    }
}
