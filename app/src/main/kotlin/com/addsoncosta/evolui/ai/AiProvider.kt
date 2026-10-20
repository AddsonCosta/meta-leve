package com.addsoncosta.evolui.ai

/** Formato da API de cada empresa. */
enum class AiProtocol { ANTHROPIC, OPENAI, GEMINI }

/** IAs que o usuário pode conectar com a própria chave. */
enum class AiProvider(
    val label: String,
    /** Empresa que recebe a foto e cobra o uso. */
    val company: String,
    val protocol: AiProtocol,
    /** Endereço base da API; nulo em [CUSTOM] (o usuário informa). */
    val baseUrl: String?,
    val defaultModel: String,
    /** Site onde a chave é criada. */
    val keyUrl: String?,
    val keyHint: String,
    val steps: List<String>,
    /** "a" ou "o", para escrever "na Anthropic", "no Google". */
    private val article: String = "a",
) {

    CLAUDE(
        label = "Claude",
        company = "Anthropic",
        protocol = AiProtocol.ANTHROPIC,
        baseUrl = "https://api.anthropic.com/v1",
        defaultModel = "claude-haiku-5-5",
        keyUrl = "https://console.anthropic.com",
        keyHint = "sk-ant-…",
        steps = listOf(
            "Abra console.anthropic.com e entre (ou crie uma conta). É o site de desenvolvedor, diferente do app Claude.",
            "Em Billing (cobrança), adicione créditos. A assinatura Claude Pro não vale aqui.",
            "Vá em API Keys e toque em Create Key. Dê um nome, por exemplo \"Evolui\".",
            "Copie a chave (começa com sk-ant-) e cole no campo abaixo.",
        ),
    ),
    CHATGPT(
        label = "ChatGPT",
        company = "OpenAI",
        protocol = AiProtocol.OPENAI,
        baseUrl = "https://api.openai.com/v1",
        defaultModel = "gpt-4o-mini",
        keyUrl = "https://platform.openai.com/api-keys",
        keyHint = "sk-…",
        steps = listOf(
            "Abra platform.openai.com e entre com sua conta. É o site de desenvolvedor, diferente do app ChatGPT.",
            "Em Settings > Billing, adicione créditos. A assinatura ChatGPT Plus não vale aqui.",
            "Vá em API keys e toque em Create new secret key.",
            "Copie a chave (começa com sk-) e cole no campo abaixo. Ela só aparece uma vez.",
        ),
    ),
    GEMINI(
        label = "Gemini",
        company = "Google",
        protocol = AiProtocol.GEMINI,
        baseUrl = "https://generativelanguage.googleapis.com/v1beta",
        defaultModel = "gemini-2.5-flash",
        keyUrl = "https://aistudio.google.com/apikey",
        keyHint = "AIza…",
        steps = listOf(
            "Abra aistudio.google.com e entre com sua conta Google.",
            "Toque em Get API key e depois em Create API key.",
            "Copie a chave (começa com AIza) e cole no campo abaixo.",
            "O Google tem uma faixa gratuita com limites. Nela, os termos do Google permitem usar o que é enviado para melhorar os produtos deles; com cobrança ativada, não. A assinatura do Gemini (Google One) não inclui chave.",
        ),
        article = "o",
    ),
    GROK(
        label = "Grok",
        company = "xAI",
        protocol = AiProtocol.OPENAI,
        baseUrl = "https://api.x.ai/v1",
        defaultModel = "grok-4",
        keyUrl = "https://console.x.ai",
        keyHint = "xai-…",
        steps = listOf(
            "Abra console.x.ai e entre (ou crie uma conta).",
            "Adicione créditos na área de cobrança (Billing). A assinatura do X Premium ou do app Grok não vale aqui.",
            "Vá em API Keys e toque em Create API Key.",
            "Copie a chave (começa com xai-) e cole no campo abaixo.",
        ),
    ),
    OPENROUTER(
        label = "OpenRouter",
        company = "OpenRouter",
        protocol = AiProtocol.OPENAI,
        baseUrl = "https://openrouter.ai/api/v1",
        defaultModel = "openai/gpt-4o-mini",
        keyUrl = "https://openrouter.ai/keys",
        keyHint = "sk-or-…",
        steps = listOf(
            "Abra openrouter.ai e entre (ou crie uma conta). Ele dá acesso a IAs de várias empresas com uma chave só.",
            "Em Credits, adicione créditos.",
            "Vá em Keys e toque em Create Key.",
            "Copie a chave (começa com sk-or-) e cole no campo abaixo.",
            "O modelo é escrito como empresa/modelo (ex.: openai/gpt-4o-mini) e precisa aceitar imagens. A foto passa pela OpenRouter e pela empresa do modelo.",
        ),
    ),
    CUSTOM(
        label = "Outra",
        company = "o serviço escolhido",
        protocol = AiProtocol.OPENAI,
        baseUrl = null,
        defaultModel = "",
        keyUrl = null,
        keyHint = "Chave do serviço",
        steps = listOf(
            "Use um serviço compatível com a API da OpenAI (chat/completions) e com um modelo que aceite imagens.",
            "Informe o endereço base da API, começando com https:// (ex.: https://exemplo.com/v1).",
            "Crie a chave no site do serviço, cole no campo abaixo e escreva o nome do modelo.",
        ),
        article = "o",
    );

    /** "da Anthropic", "do Google". */
    val ofCompany: String get() = "d$article $company"

    /** "na Anthropic", "no Google". */
    val atCompany: String get() = "n$article $company"

    companion object {
        fun byName(name: String?): AiProvider? = entries.firstOrNull { it.name == name }
    }
}

/** Conexão escolhida pelo usuário. [key] nunca deve ir para logs. */
data class AiConfig(
    val provider: AiProvider,
    val key: String,
    val model: String,
    /** Só para [AiProvider.CUSTOM]. */
    val customBaseUrl: String? = null,
) {
    val baseUrl: String get() = (provider.baseUrl ?: customBaseUrl.orEmpty()).trim().trimEnd('/')
    val effectiveModel: String get() = model.trim().ifEmpty { provider.defaultModel }

    override fun toString(): String = "AiConfig(provider=$provider, model=$effectiveModel)"
}
