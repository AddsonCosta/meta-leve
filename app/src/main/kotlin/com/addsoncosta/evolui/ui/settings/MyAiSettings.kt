package com.addsoncosta.evolui.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.addsoncosta.evolui.ai.AiClient
import com.addsoncosta.evolui.ai.AiConfig
import com.addsoncosta.evolui.ai.AiConfigStore
import com.addsoncosta.evolui.ai.AiException
import com.addsoncosta.evolui.ai.AiProvider
import com.addsoncosta.evolui.ai.AiSummary
import com.addsoncosta.evolui.ui.components.Overline
import com.addsoncosta.evolui.ui.components.Panel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Cartão "Minha IA" das Configurações; abre a tela para conectar a IA do usuário. */
@Composable
fun MyAiSettingsEntry() {
    val context = LocalContext.current
    val store = remember { AiConfigStore(context) }
    var summary by remember { mutableStateOf(store.summary()) }
    var open by remember { mutableStateOf(false) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Panel {
        Text("Minha IA", style = MaterialTheme.typography.titleMedium)
        val s = summary
        if (s == null) {
            Text(
                "Opcional. Conecte a sua própria IA (Claude, ChatGPT, Gemini, Grok ou outra) para reconhecer pratos pela foto " +
                    "quando o celular não souber. Você usa a sua chave; o custo fica na sua conta da empresa escolhida.",
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            Text("Conectada: ${s.provider.label} (${s.provider.company})", fontWeight = FontWeight.Bold)
            Text(
                "Modelo: ${s.model}" + (if (s.keyTail.isNotEmpty()) " · chave terminada em ${s.keyTail}" else ""),
                style = MaterialTheme.typography.bodySmall,
                color = muted,
            )
            Text(
                "A foto só sai do celular quando você toca em \"Analisar com minha IA\".",
                style = MaterialTheme.typography.bodySmall,
                color = muted,
            )
        }
        if (s == null) {
            Button(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) { Text("Conectar minha IA") }
        } else {
            OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) { Text("Gerenciar") }
        }
    }

    if (open) {
        MyAiDialog(onDismiss = {
            open = false
            summary = store.summary()
        })
    }
}

private sealed interface TestState {
    data object Idle : TestState
    data object Running : TestState
    data class Ok(val message: String) : TestState
    data class Failed(val message: String) : TestState
}

/** Tela cheia para escolher a IA, colar a chave, testar e desconectar. Também aberta pelo Prato por foto. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MyAiDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { AiConfigStore(context) }
    var saved by remember { mutableStateOf<AiSummary?>(store.summary()) }
    var provider by remember { mutableStateOf(saved?.provider ?: AiProvider.CLAUDE) }
    var model by remember { mutableStateOf(saved?.model ?: provider.defaultModel) }
    var baseUrl by remember { mutableStateOf(saved?.customBaseUrl.orEmpty()) }
    var key by remember { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }
    var state by remember { mutableStateOf<TestState>(TestState.Idle) }
    var job by remember { mutableStateOf<Job?>(null) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    // A chave guardada só vale para a mesma IA; ao trocar de IA é preciso colar outra.
    val keepsSavedKey = key.isBlank() && saved?.provider == provider
    val canUse = (key.isNotBlank() || keepsSavedKey) &&
        model.isNotBlank() &&
        (provider != AiProvider.CUSTOM || baseUrl.trim().startsWith("https://"))

    fun openSite(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
            state = TestState.Failed("Não encontrei um navegador. Abra $url no computador ou em outro aparelho.")
        }
    }

    /** Monta a conexão com o que está na tela (a chave guardada é lida fora da thread principal). */
    suspend fun current(): AiConfig? {
        val k = key.trim().ifEmpty {
            if (keepsSavedKey) withContext(Dispatchers.IO) { store.load()?.key }.orEmpty() else ""
        }
        if (k.isEmpty()) return null
        return AiConfig(provider, k, model.trim(), if (provider == AiProvider.CUSTOM) baseUrl.trim() else null)
    }

    fun test() {
        job?.cancel()
        job = scope.launch {
            state = TestState.Running
            val cfg = current()
            if (cfg == null) {
                state = TestState.Failed("Não consegui ler a chave guardada. Cole a chave de novo.")
                return@launch
            }
            state = try {
                AiClient.test(cfg)
                withContext(Dispatchers.IO) { store.save(cfg) }
                saved = store.summary()
                key = ""
                TestState.Ok("Conexão funcionando. Sua IA está conectada e a chave foi guardada criptografada neste celular.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: AiException) {
                TestState.Failed(e.message ?: "Não deu certo. Tente de novo.")
            } catch (e: Exception) {
                TestState.Failed("Não deu certo. Confira a internet e tente de novo.")
            }
        }
    }

    fun saveOnly() {
        job?.cancel()
        job = scope.launch {
            val cfg = current()
            if (cfg == null) {
                state = TestState.Failed("Cole a chave de API.")
                return@launch
            }
            withContext(Dispatchers.IO) { store.save(cfg) }
            saved = store.summary()
            key = ""
            state = TestState.Ok("Salvo. Se quiser, toque em Testar conexão para conferir.")
        }
    }

    fun disconnect() {
        job?.cancel()
        store.clear()
        saved = null
        key = ""
        state = TestState.Ok("Desconectado. A chave foi apagada deste celular.")
    }

    Dialog(
        onDismissRequest = {
            job?.cancel()
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        job?.cancel()
                        onDismiss()
                    }) { Icon(Icons.Filled.Close, "Fechar") }
                    Column {
                        Overline("Configurações")
                        Text("Minha IA", style = MaterialTheme.typography.headlineMedium)
                    }
                }
                Text(
                    "Use a sua própria conta de IA para reconhecer pratos pela foto quando o celular não souber (e, onde o app oferecer, ler documentos). " +
                        "O Evolui não tem servidor: o app fala direto com a empresa que você escolher, usando a sua chave.",
                    style = MaterialTheme.typography.bodyMedium,
                )

                Panel {
                    Text("Antes de conectar", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Bullet(
                        "Quando você toca em \"Analisar com minha IA\" (num prato ou num documento de saúde), a foto ou o documento " +
                            "sai do celular e vai para a empresa escolhida (Anthropic, OpenAI, Google, xAI, OpenRouter ou outra). " +
                            "Sem esse toque, nada é enviado."
                    )
                    Bullet("O custo é cobrado na sua conta nessa empresa, pela tabela de preços dela. O Evolui não cobra nada.")
                    Bullet(
                        "Assinaturas como ChatGPT Plus, Claude Pro ou Gemini (Google One) não incluem chave de API. " +
                            "A chave é criada no site de desenvolvedor de cada empresa, com cobrança à parte."
                    )
                    Bullet("A chave fica guardada criptografada só neste celular e não entra em backups.")
                }

                Text("Escolha a IA", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AiProvider.entries.forEach { p ->
                        FilterChip(
                            selected = provider == p,
                            onClick = {
                                if (provider != p) {
                                    provider = p
                                    model = if (saved?.provider == p) saved?.model ?: p.defaultModel else p.defaultModel
                                    key = ""
                                    state = TestState.Idle
                                }
                            },
                            label = { Text(p.label) },
                        )
                    }
                }

                Panel {
                    Text(
                        if (provider == AiProvider.CUSTOM) "Como conectar outra IA" else "Como criar a chave ${provider.ofCompany}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    provider.steps.forEachIndexed { i, step ->
                        Text("${i + 1}. $step", style = MaterialTheme.typography.bodySmall)
                    }
                    provider.keyUrl?.let { url ->
                        OutlinedButton(onClick = { openSite(url) }, modifier = Modifier.fillMaxWidth()) {
                            Text("Abrir ${url.removePrefix("https://").substringBefore('/')}")
                        }
                    }
                }

                if (provider == AiProvider.CUSTOM) {
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it.trim() },
                        label = { Text("Endereço da API") },
                        placeholder = { Text("https://exemplo.com/v1") },
                        supportingText = { Text("Compatível com OpenAI (o app acrescenta /chat/completions).") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it.trim() },
                    label = { Text("Chave de API") },
                    placeholder = { Text(provider.keyHint) },
                    supportingText = {
                        val s = saved
                        Text(
                            if (s != null && s.provider == provider) {
                                "Já há uma chave guardada" + (if (s.keyTail.isNotEmpty()) " (termina em ${s.keyTail})" else "") +
                                    ". Deixe vazio para mantê-la."
                            } else {
                                "Cole a chave inteira, sem espaços."
                            }
                        )
                    },
                    singleLine = true,
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(
                                if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                if (showKey) "Esconder chave" else "Mostrar chave",
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it.trim() },
                    label = { Text("Modelo") },
                    supportingText = {
                        Text(
                            if (provider.defaultModel.isEmpty()) "Nome do modelo no serviço. Precisa aceitar imagens."
                            else "Padrão: ${provider.defaultModel}. Troque só se souber o nome de outro modelo que aceite imagens."
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (provider.defaultModel.isNotEmpty() && model != provider.defaultModel) {
                    TextButton(onClick = { model = provider.defaultModel }) { Text("Voltar ao modelo padrão") }
                }

                when (val s = state) {
                    TestState.Idle -> Unit
                    TestState.Running -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(24.dp))
                        Text("  Testando a conexão…")
                    }
                    is TestState.Ok -> Text(s.message, color = MaterialTheme.colorScheme.primary)
                    is TestState.Failed -> Text(s.message, color = MaterialTheme.colorScheme.error)
                }

                Button(
                    onClick = { test() },
                    enabled = canUse && state != TestState.Running,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Testar conexão") }
                Text(
                    "O teste manda uma pergunta curta, só com texto (sem foto), e pode gerar um custo mínimo na sua conta.",
                    style = MaterialTheme.typography.bodySmall,
                    color = muted,
                )
                TextButton(
                    onClick = { saveOnly() },
                    enabled = canUse && state != TestState.Running,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Salvar sem testar") }
                if (saved != null) {
                    OutlinedButton(onClick = { disconnect() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Desconectar e apagar a chave", color = MaterialTheme.colorScheme.error)
                    }
                }
                Text(
                    "Para cancelar de vez, apague também a chave no site da empresa.",
                    style = MaterialTheme.typography.bodySmall,
                    color = muted,
                    modifier = Modifier.padding(bottom = 24.dp),
                )
            }
        }
    }
}

@Composable
private fun Bullet(text: String) {
    Text("• $text", style = MaterialTheme.typography.bodySmall)
}
