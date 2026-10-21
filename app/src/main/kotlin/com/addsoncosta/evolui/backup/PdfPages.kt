package com.addsoncosta.evolui.backup

import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File

/** Número de páginas de um PDF guardado no celular, ou nulo se não der para abrir. */
fun pdfPageCount(file: File): Int? = runCatching {
    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
        val renderer = PdfRenderer(fd)
        try {
            renderer.pageCount
        } finally {
            renderer.close()
        }
    }
}.getOrNull()
