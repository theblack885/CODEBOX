package com.hwstudio.app

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.hoho.android.usbserial.driver.UsbSerialDriver
import kotlin.concurrent.thread

data class ChatMsg(val role: String, val text: String)

class HwViewModel(app: Application) : AndroidViewModel(app) {

    private val main = Handler(Looper.getMainLooper())
    private val prefs = app.getSharedPreferences("hw", Context.MODE_PRIVATE)

    val serialLog = mutableStateListOf<String>()
    val drivers = mutableStateListOf<UsbSerialDriver>()
    val chat = mutableStateListOf<ChatMsg>()

    var status by mutableStateOf("Connect nahi hai")
    var connected by mutableStateOf(false)
    var progress by mutableStateOf(0f)
    var busy by mutableStateOf(false)
    var aiBusy by mutableStateOf(false)
    var baud by mutableStateOf(115200)
    var code by mutableStateOf("// Yahan apna Arduino/ESP code likho ya AI tab se paste karo\n\nvoid setup() {\n  pinMode(LED_BUILTIN, OUTPUT);\n}\n\nvoid loop() {\n  digitalWrite(LED_BUILTIN, HIGH);\n  delay(500);\n  digitalWrite(LED_BUILTIN, LOW);\n  delay(500);\n}\n")
    var apiKey by mutableStateOf(prefs.getString("key", "") ?: "")
    var model by mutableStateOf(prefs.getString("model", "claude-sonnet-5-5") ?: "claude-sonnet-5-5")
    var board by mutableStateOf("Arduino Uno")

    val link = SerialLink(app) { line ->
        main.post {
            serialLog.add(line)
            if (serialLog.size > 400) serialLog.removeRange(0, serialLog.size - 400)
        }
    }

    private fun post(f: () -> Unit) { main.post(f) }
    fun log(s: String) = post { serialLog.add("» $s") }

    fun saveSettings() {
        prefs.edit().putString("key", apiKey).putString("model", model).apply()
    }

    fun scan() {
        drivers.clear()
        drivers.addAll(link.list())
        status = if (drivers.isEmpty()) "Koi USB device nahi mila (OTG cable check karo)" else "${drivers.size} device mile"
    }

    fun connect(d: UsbSerialDriver) {
        link.connect(d, baud) { ok, msg ->
            post {
                connected = ok
                status = msg
            }
        }
    }

    fun disconnect() {
        link.close()
        connected = false
        status = "Disconnected"
    }

    fun send(cmd: String) {
        if (!connected) { status = "Pehle connect karo"; return }
        link.writeLine(cmd)
        serialLog.add("> $cmd")
    }

    private fun readText(uri: Uri): ByteArray? =
        getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.readBytes() }

    private fun upload(hexText: String) {
        if (!connected) { status = "Pehle board connect karo"; return }
        if (busy) return
        busy = true
        progress = 0f
        thread {
            val hex = Stk500.parseHex(hexText)
            if (hex.isEmpty()) {
                log("Hex file khaali ya galat hai")
            } else {
                val ok = Stk500.upload(link, hex, { log(it) }, { p -> post { progress = p } })
                post { status = if (ok) "Upload ho gaya" else "Upload fail" }
            }
            post { busy = false }
        }
    }

    fun uploadHexFile(uri: Uri) {
        val bytes = readText(uri) ?: run { log("File nahi khuli"); return }
        upload(String(bytes))
    }

    fun uploadBridge() {
        val app = getApplication<Application>()
        val text = try {
            app.assets.open("bridge_uno.hex").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            log("bridge_uno.hex APK me nahi hai (GitHub workflow se build karo)")
            return
        }
        upload(text)
    }

    fun bridgeSource(): String =
        getApplication<Application>().assets.open("hw_bridge.ino").bufferedReader().use { it.readText() }

    fun askAi(prompt: String) {
        if (prompt.isBlank() || aiBusy) return
        chat.add(ChatMsg("user", prompt))
        aiBusy = true
        val hist = chat.map { Pair(it.role, it.text) }
        val ctx = "Selected board: $board\nCurrent editor code:\n${code.take(6000)}\nRecent serial log:\n${serialLog.takeLast(15).joinToString("\n")}"
        thread {
            val reply = AiClient.ask(apiKey, model, hist, ctx)
            post {
                chat.add(ChatMsg("assistant", reply))
                aiBusy = false
            }
        }
    }
}
