package com.addsoncosta.evolui.ui.health

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.addsoncosta.evolui.health.HealthReports
import com.addsoncosta.evolui.health.ReportArea
import com.addsoncosta.evolui.ui.share.copyText
import com.addsoncosta.evolui.ui.share.shareImage
import com.addsoncosta.evolui.ui.share.shareTextOnly
import java.io.File
import kotlinx.coroutines.launch

/**
 * Botão de compartilhar de uma área da Saúde: PDF, imagem, texto ou copiar.
 */
@Composable
fun HealthShareButton(area: ReportArea, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }, modifier = modifier) { Icon(Icons.Filled.Share, "Compartilhar ${area.label}") }
    if (open) {
        ShareChoicesSheet(
            title = area.label,
            onDismiss = { open = false },
            pdf = { HealthReports.pdf(context, area) },
            text = { HealthReports.text(context, area) },
            image = { HealthReports.image(context, area) },
        )
    }
}

/** O mesmo, para um registro só (uma consulta, um exame...). */
@Composable
fun RecordShareButton(recordId: Long, title: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }, modifier = modifier) { Icon(Icons.Filled.Share, "Compartilhar") }
    if (open) {
        ShareChoicesSheet(
            title = title,
            onDismiss = { open = false },
            pdf = { HealthReports.recordPdf(context, recordId) },
            text = { HealthReports.recordText(context, recordId) },
            image = { HealthReports.recordImage(context, recordId) },
        )
    }
}

/** Painel com as formas de compartilhar. Cada opção gera o conteúdo na hora, fora da tela principal. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareChoicesSheet(
    title: String,
    onDismiss: () -> Unit,
    pdf: suspend () -> File,
    text: suspend () -> String,
    image: suspend () -> Bitmap,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf<String?>(null) }

    fun launchAction(key: String, action: suspend () -> Unit) {
        if (busy != null) return
        busy = key
        scope.launch {
            try {
                action()
                onDismiss()
            } catch (e: Exception) {
                Toast.makeText(context, "Não foi possível gerar agora. Tente de novo.", Toast.LENGTH_LONG).show()
            } finally {
                busy = null
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Compartilhar", style = MaterialTheme.typography.titleLarge)
            Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "Seus dados ficam só no celular. Eles só saem daqui quando você escolhe para quem enviar.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            ShareOption(Icons.Filled.PictureAsPdf, "PDF", "Relatório completo para enviar ao médico ou imprimir", busy == "pdf") {
                launchAction("pdf") { sharePdf(context, pdf()) }
            }
            ShareOption(Icons.Filled.Visibility, "Ver o PDF", "Abre o relatório no leitor de PDF do celular", busy == "view") {
                launchAction("view") { openPdf(context, pdf()) }
            }
            ShareOption(Icons.Filled.Image, "Imagem", "Cartão-resumo para WhatsApp ou redes sociais", busy == "image") {
                launchAction("image") { shareImage(context, image(), title) }
            }
            ShareOption(Icons.Filled.ChatBubble, "Texto", "Mensagem de texto simples, ótima para WhatsApp", busy == "text") {
                launchAction("text") { shareTextOnly(context, text()) }
            }
            ShareOption(Icons.Filled.ContentCopy, "Copiar", "Copia o texto para colar onde quiser", busy == "copy") {
                launchAction("copy") {
                    copyText(context, text())
                    Toast.makeText(context, "Texto copiado", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

@Composable
private fun ShareOption(icon: ImageVector, title: String, subtitle: String, busy: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (busy) CircularProgressIndicator(Modifier.size(28.dp)) else Icon(icon, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Envia o PDF gerado (pasta cache/share) para outro app. */
fun sharePdf(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Compartilhar PDF").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
}

/** Abre o PDF gerado no leitor do celular. */
fun openPdf(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val view = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/pdf")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(view)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "Nenhum leitor de PDF encontrado. Use \"PDF\" para enviar.", Toast.LENGTH_LONG).show()
    }
}
