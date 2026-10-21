package com.addsoncosta.evolui.backup

import android.content.Context
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Fotos e anexos que não vieram no último backup restaurado (backup "sem fotos e anexos").
 * Fica num arquivo na pasta interna, para a tela mostrar "Anexo não incluído no backup" até a pessoa anexar de novo.
 */
class MissingFiles(context: Context) {
    private val file = File(context.filesDir, "backup_ausentes.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(OmittedFile.serializer())

    private val _items = MutableStateFlow(load())
    /** Itens ainda sem arquivo. */
    val items: StateFlow<List<OmittedFile>> = _items.asStateFlow()

    /** O item do arquivo [fileName] (anexo da Saúde ou foto), se ele ficou de fora do backup. */
    fun forFile(fileName: String): OmittedFile? = _items.value.firstOrNull { it.fileName == fileName }

    /** Itens de um registro da Saúde. */
    fun forRecord(recordId: Long): List<OmittedFile> = _items.value.filter { it.recordId == recordId }

    @Synchronized
    fun replaceAll(list: List<OmittedFile>) {
        _items.value = list
        save(list)
    }

    /** Tira o item da lista (o arquivo foi anexado de novo ou o registro foi apagado). */
    @Synchronized
    fun remove(fileName: String) {
        val list = _items.value.filterNot { it.fileName == fileName }
        if (list.size != _items.value.size) replaceAll(list)
    }

    private fun load(): List<OmittedFile> = runCatching {
        if (file.exists()) json.decodeFromString(serializer, file.readText()) else emptyList()
    }.getOrDefault(emptyList())

    private fun save(list: List<OmittedFile>) {
        runCatching {
            if (list.isEmpty()) file.delete() else file.writeText(json.encodeToString(serializer, list))
        }
    }
}
