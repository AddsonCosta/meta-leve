package com.addsoncosta.evolui.ai

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AiWireTest {
    private fun config(p: AiProvider, key: String = "sk-test-123456", model: String = "", url: String? = null) =
        AiConfig(p, key, model, url)

    private fun body(r: AiHttpRequest): JsonObject = AiJson.json.parseToJsonElement(r.body).jsonObject

    private fun str(o: JsonObject?, k: String) = (o?.get(k) as? JsonPrimitive)?.content

    @Test
    fun anthropicRequest() {
        val r = AiWire.request(config(AiProvider.CLAUDE), "sys", "olá", listOf("AAAA"))
        assertEquals("https://api.anthropic.com/v1/messages", r.url)
        assertEquals("sk-test-123456", r.headers["x-api-key"])
        assertEquals("2023-06-01", r.headers["anthropic-version"])
        val b = body(r)
        assertEquals("claude-haiku-5-5", str(b, "model"))
        assertEquals("sys", str(b, "system"))
        val content = b["messages"]!!.jsonArray[0].jsonObject["content"]!!.jsonArray
        assertEquals("image", str(content[0].jsonObject, "type"))
        assertEquals("AAAA", str(content[0].jsonObject["source"]?.jsonObject, "data"))
        assertEquals("olá", str(content[1].jsonObject, "text"))
    }

    @Test
    fun openAiRequestUsesDataUrlAndNewTokenField() {
        val r = AiWire.request(config(AiProvider.CHATGPT), "sys", "olá", listOf("BBBB"))
        assertEquals("https://api.openai.com/v1/chat/completions", r.url)
        assertEquals("Bearer sk-test-123456", r.headers["Authorization"])
        val b = body(r)
        assertEquals("gpt-4o-mini", str(b, "model"))
        assertNotNull(b["max_completion_tokens"])
        assertNull(b["max_tokens"])
        val user = b["messages"]!!.jsonArray[1].jsonObject["content"]!!.jsonArray
        assertEquals("data:image/jpeg;base64,BBBB", str(user[0].jsonObject["image_url"]?.jsonObject, "url"))
    }

    @Test
    fun compatibleProvidersUseTheirBaseUrl() {
        assertEquals("https://api.x.ai/v1/chat/completions", AiWire.request(config(AiProvider.GROK), "s", "u", emptyList()).url)
        assertEquals("https://openrouter.ai/api/v1/chat/completions", AiWire.request(config(AiProvider.OPENROUTER), "s", "u", emptyList()).url)
        val custom = config(AiProvider.CUSTOM, model = "meu-modelo", url = "https://exemplo.com/v1/")
        val r = AiWire.request(custom, "s", "u", emptyList())
        assertEquals("https://exemplo.com/v1/chat/completions", r.url)
        assertEquals("meu-modelo", str(body(r), "model"))
        assertNotNull(body(r)["max_tokens"])
    }

    @Test
    fun geminiRequest() {
        val r = AiWire.request(config(AiProvider.GEMINI, model = "models/gemini-2.5-flash"), "sys", "olá", listOf("CCCC"))
        assertEquals("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent", r.url)
        assertEquals("sk-test-123456", r.headers["x-goog-api-key"])
        assertFalse(r.url.contains("key="))
        val parts = body(r)["contents"]!!.jsonArray[0].jsonObject["parts"]!!.jsonArray
        assertEquals("CCCC", str(parts[0].jsonObject["inlineData"]?.jsonObject, "data"))
        assertEquals("olá", str(parts[1].jsonObject, "text"))
    }

    @Test
    fun keyNeverInToString() {
        val c = config(AiProvider.CLAUDE)
        assertFalse(c.toString().contains("sk-test"))
        assertFalse(AiWire.request(c, "s", "u", emptyList()).toString().contains("sk-test"))
    }

    @Test
    fun readsResponses() {
        val anthropic = """{"content":[{"type":"thinking","thinking":"x"},{"type":"text","text":"{\"ok\":true}"}]}"""
        assertEquals("{\"ok\":true}", AiWire.responseText(AiProtocol.ANTHROPIC, anthropic))
        val openai = """{"choices":[{"message":{"role":"assistant","content":"{\"ok\":true}"}}]}"""
        assertEquals("{\"ok\":true}", AiWire.responseText(AiProtocol.OPENAI, openai))
        val gemini = """{"candidates":[{"content":{"parts":[{"text":"{\"ok\":"},{"text":"true}"}]}}]}"""
        assertEquals("{\"ok\":true}", AiWire.responseText(AiProtocol.GEMINI, gemini))
    }

    @Test
    fun emptyOrBrokenResponseIsBadFormat() {
        for (b in listOf("", "<html>", """{"choices":[]}""", """{"content":[]}""")) {
            try {
                AiWire.responseText(AiProtocol.OPENAI, b)
                AiWire.responseText(AiProtocol.ANTHROPIC, b)
                fail("deveria falhar: $b")
            } catch (e: AiException) {
                assertEquals(AiErrorKind.BAD_FORMAT, e.kind)
            }
        }
    }

    @Test
    fun errorMapping() {
        val c = config(AiProvider.CHATGPT)
        assertEquals(AiErrorKind.INVALID_KEY, AiWire.error(401, """{"error":{"message":"Incorrect API key"}}""", c).kind)
        assertEquals(AiErrorKind.CREDITS, AiWire.error(429, """{"error":{"message":"quota","code":"insufficient_quota"}}""", c).kind)
        assertEquals(AiErrorKind.LIMIT, AiWire.error(429, """{"error":{"message":"Rate limit reached"}}""", c).kind)
        assertEquals(AiErrorKind.CREDITS, AiWire.error(402, "", config(AiProvider.OPENROUTER)).kind)
        assertEquals(AiErrorKind.MODEL, AiWire.error(404, """{"error":{"message":"The model x does not exist"}}""", c).kind)
        assertEquals(AiErrorKind.SERVER, AiWire.error(503, "", c).kind)
        val claude = config(AiProvider.CLAUDE)
        val low = """{"type":"error","error":{"type":"invalid_request_error","message":"Your credit balance is too low to access the Anthropic API."}}"""
        assertEquals(AiErrorKind.CREDITS, AiWire.error(400, low, claude).kind)
        val gemini = config(AiProvider.GEMINI)
        val bad = """{"error":{"code":400,"message":"API key not valid. Please pass a valid API key.","status":"INVALID_ARGUMENT"}}"""
        assertEquals(AiErrorKind.INVALID_KEY, AiWire.error(400, bad, gemini).kind)
        assertTrue(AiWire.error(401, "", gemini).message!!.contains("do Google"))
    }

    @Test
    fun providerMessageHidesKey() {
        val c = config(AiProvider.CUSTOM, key = "segredo-123456", model = "m", url = "https://x.com/v1")
        val e = AiWire.error(400, """{"error":{"message":"bad key segredo-123456 here"}}""", c)
        assertFalse(e.message!!.contains("segredo-123456"))
    }

    @Test
    fun extractObjectPicksFirstValid() {
        assertEquals(JsonPrimitive(true), AiJson.extractObject("bla { nao é json } depois {\"ok\": true} fim")?.get("ok"))
        assertNull(AiJson.extractObject("[1, 2]"))
        assertTrue(AiJson.extractObject("```\n{\"a\": [1]}\n```")?.get("a") is JsonArray)
    }
}
