package com.hwstudio.app

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object AiClient {

    const val GROQ_DEFAULT_MODEL = "llama-3.3-70b-versatile"

    const val SYSTEM = """Tum "Hardware Studio" ke andar built-in hardware coding assistant ho.
User Hindi/Hinglish me baat karta hai - usi bhasha me jawab do.
Jab user kisi bhi hardware (Arduino Uno/Nano/Mega, ESP32, ESP8266, Raspberry Pi Pico, STM32, ATtiny, etc.) ke liye code maange:
1. Poora, seedha chalne wala code ek code block me do (language likho: cpp, python, etc.).
2. Code ke upar ya neeche saaf batao: kaunsa board select karna hai, kaunsi library install karni hai, code kahan paste karna hai (jaise "Code tab me poora paste karo"), kaunsi file ka naam rakhna hai, aur wiring (kaunsa pin kahan).
3. Agar user ke paas sirf phone hai to batao ki .hex compile PC app (Hardware Studio EXE) ya cloud se banana padega, phir APK se upload hoga.
4. Chhota aur practical raho, galat pin ya khatarnak voltage ke baare me chetavni do."""

    /** Key "gsk_" se shuru ho to Groq, warna Anthropic. */
    fun isGroq(key: String) = key.trim().startsWith("gsk_")

    /** history = list of (role, text), role = "user" | "assistant". Blocking call - IO thread se chalao. */
    fun ask(keyRaw: String, model: String, history: List<Pair<String, String>>, context: String): String {
        val key = keyRaw.trim()
        if (key.isBlank()) return "API key daalo (AI tab -> Settings)."
        val system = SYSTEM + if (context.isBlank()) "" else "\n\nContext:\n$context"
        return try {
            if (isGroq(key)) askGroq(key, model, history, system) else askAnthropic(key, model, history, system)
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    private fun post(url: String, headers: Map<String, String>, body: JSONObject): Pair<Int, String> {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.connectTimeout = 20000
        c.readTimeout = 120000
        c.setRequestProperty("content-type", "application/json")
        c.setRequestProperty("User-Agent", "HardwareStudio/1.0")
        for ((k, v) in headers) c.setRequestProperty(k, v)
        c.doOutput = true
        c.outputStream.use { it.write(body.toString().toByteArray()) }
        val code = c.responseCode
        val stream = if (code in 200..299) c.inputStream else c.errorStream
        val text = stream.bufferedReader().use { it.readText() }
        return Pair(code, text)
    }

    private fun askAnthropic(key: String, model: String, history: List<Pair<String, String>>, system: String): String {
        val msgs = JSONArray()
        for ((role, text) in history) msgs.put(JSONObject().put("role", role).put("content", text))
        val body = JSONObject()
            .put("model", model)
            .put("max_tokens", 4096)
            .put("system", system)
            .put("messages", msgs)
        val (code, text) = post(
            "https://api.anthropic.com/v1/messages",
            mapOf("x-api-key" to key, "anthropic-version" to "2023-06-01"),
            body
        )
        if (code !in 200..299) return "Error $code: ${text.take(400)}"
        val arr = JSONObject(text).getJSONArray("content")
        val sb = StringBuilder()
        for (i in 0 until arr.length()) sb.append(arr.getJSONObject(i).optString("text"))
        return sb.toString()
    }

    private fun askGroq(key: String, model: String, history: List<Pair<String, String>>, system: String): String {
        // Settings me abhi Claude ka naam ho to Groq ka model use karo
        val m = if (model.isBlank() || model.startsWith("claude")) GROQ_DEFAULT_MODEL else model
        val msgs = JSONArray()
        msgs.put(JSONObject().put("role", "system").put("content", system))
        for ((role, text) in history) msgs.put(JSONObject().put("role", role).put("content", text))
        val body = JSONObject()
            .put("model", m)
            .put("max_tokens", 4096)
            .put("messages", msgs)
        val (code, text) = post(
            "https://api.groq.com/openai/v1/chat/completions",
            mapOf("Authorization" to "Bearer $key"),
            body
        )
        if (code !in 200..299) return "Error $code: ${text.take(400)}"
        return JSONObject(text).getJSONArray("choices").getJSONObject(0)
            .getJSONObject("message").optString("content")
    }

    fun extractCode(text: String): String? {
        val re = Regex("```[a-zA-Z+]*\\n(.*?)```", RegexOption.DOT_MATCHES_ALL)
        return re.findAll(text).map { it.groupValues[1] }.maxByOrNull { it.length }
    }
}
