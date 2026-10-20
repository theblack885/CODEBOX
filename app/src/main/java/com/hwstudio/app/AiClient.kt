package com.hwstudio.app

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object AiClient {

    const val SYSTEM = """Tum "Hardware Studio" ke andar built-in hardware coding assistant ho.
User Hindi/Hinglish me baat karta hai - usi bhasha me jawab do.
Jab user kisi bhi hardware (Arduino Uno/Nano/Mega, ESP32, ESP8266, Raspberry Pi Pico, STM32, ATtiny, etc.) ke liye code maange:
1. Poora, seedha chalne wala code ek code block me do (language likho: cpp, python, etc.).
2. Code ke upar ya neeche saaf batao: kaunsa board select karna hai, kaunsi library install karni hai, code kahan paste karna hai (jaise "Code tab me poora paste karo"), kaunsi file ka naam rakhna hai, aur wiring (kaunsa pin kahan).
3. Agar user ke paas sirf phone hai to batao ki .hex compile PC app (Hardware Studio EXE) ya cloud se banana padega, phir APK se upload hoga.
4. Chhota aur practical raho, galat pin ya khatarnak voltage ke baare me chetavni do."""

    /** history = list of (role, text), role = "user" | "assistant". Blocking call - IO thread se chalao. */
    fun ask(key: String, model: String, history: List<Pair<String, String>>, context: String): String {
        if (key.isBlank()) return "API key daalo (AI tab -> Settings)."
        return try {
            val c = URL("https://api.anthropic.com/v1/messages").openConnection() as HttpURLConnection
            c.requestMethod = "POST"
            c.connectTimeout = 20000
            c.readTimeout = 120000
            c.setRequestProperty("x-api-key", key)
            c.setRequestProperty("anthropic-version", "2023-06-01")
            c.setRequestProperty("content-type", "application/json")
            c.doOutput = true

            val msgs = JSONArray()
            for ((role, text) in history) {
                msgs.put(JSONObject().put("role", role).put("content", text))
            }
            val body = JSONObject()
                .put("model", model)
                .put("max_tokens", 4096)
                .put("system", SYSTEM + if (context.isBlank()) "" else "\n\nContext:\n$context")
                .put("messages", msgs)
            c.outputStream.use { it.write(body.toString().toByteArray()) }

            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val text = stream.bufferedReader().use { it.readText() }
            if (code !in 200..299) return "Error $code: ${text.take(400)}"
            val arr = JSONObject(text).getJSONArray("content")
            val sb = StringBuilder()
            for (i in 0 until arr.length()) sb.append(arr.getJSONObject(i).optString("text"))
            sb.toString()
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    fun extractCode(text: String): String? {
        val re = Regex("```[a-zA-Z+]*\\n(.*?)```", RegexOption.DOT_MATCHES_ALL)
        return re.findAll(text).map { it.groupValues[1] }.maxByOrNull { it.length }
    }
}
