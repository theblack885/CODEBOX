package com.hwstudio.app

import com.hoho.android.usbserial.driver.UsbSerialPort

/**
 * Arduino Uno / Nano (Optiboot, STK500v1) ke liye .hex uploader.
 * Sirf ATmega328P boards (page = 128 bytes) ke liye.
 */
object Stk500 {

    private const val PAGE = 128

    fun parseHex(text: String): ByteArray {
        val mem = java.io.ByteArrayOutputStream()
        val map = java.util.TreeMap<Int, Int>()
        var upper = 0
        for (raw in text.lines()) {
            val line = raw.trim()
            if (!line.startsWith(":")) continue
            val len = line.substring(1, 3).toInt(16)
            val addr = line.substring(3, 7).toInt(16)
            val type = line.substring(7, 9).toInt(16)
            when (type) {
                0 -> for (i in 0 until len) {
                    map[upper + addr + i] = line.substring(9 + i * 2, 11 + i * 2).toInt(16)
                }
                2 -> upper = line.substring(9, 13).toInt(16) shl 4
                4 -> upper = line.substring(9, 13).toInt(16) shl 16
                1 -> break
            }
        }
        if (map.isEmpty()) return ByteArray(0)
        val size = map.lastKey() + 1
        val out = ByteArray(((size + PAGE - 1) / PAGE) * PAGE) { 0xFF.toByte() }
        for ((a, v) in map) out[a] = v.toByte()
        mem.close()
        return out
    }

    private fun readN(p: UsbSerialPort, n: Int, timeoutMs: Int): ByteArray? {
        val out = ByteArray(n)
        var got = 0
        val end = System.currentTimeMillis() + timeoutMs
        val tmp = ByteArray(64)
        while (got < n && System.currentTimeMillis() < end) {
            val c = p.read(tmp, 50)
            for (i in 0 until c) if (got < n) out[got++] = tmp[i]
        }
        return if (got == n) out else null
    }

    private fun drain(p: UsbSerialPort) {
        val tmp = ByteArray(64)
        while (p.read(tmp, 20) > 0) { /* discard */ }
    }

    private fun cmd(p: UsbSerialPort, data: ByteArray): Boolean {
        p.write(data, 1000)
        val r = readN(p, 2, 1000) ?: return false
        return r[0] == 0x14.toByte() && r[1] == 0x10.toByte()
    }

    fun upload(
        link: SerialLink,
        hex: ByteArray,
        log: (String) -> Unit,
        progress: (Float) -> Unit
    ): Boolean {
        val p = link.port ?: run { log("Pehle board connect karo"); return false }
        val oldBaud = link.currentBaud()
        link.pause()
        try {
            link.setBaud(115200)
            log("Board reset ho raha hai...")
            p.dtr = true; p.rts = true
            Thread.sleep(250)
            p.dtr = false; p.rts = false
            Thread.sleep(50)
            drain(p)

            var synced = false
            for (i in 1..12) {
                if (cmd(p, byteArrayOf(0x30, 0x20))) { synced = true; break }
                Thread.sleep(80)
            }
            if (!synced) {
                log("Bootloader se sync nahi hua. Uno/Nano (Optiboot) hai? Baud 115200?")
                return false
            }
            log("Sync OK")
            if (!cmd(p, byteArrayOf(0x50, 0x20))) { log("Programming mode fail"); return false }

            var off = 0
            while (off < hex.size) {
                val word = off / 2
                if (!cmd(p, byteArrayOf(0x55, (word and 0xFF).toByte(), ((word shr 8) and 0xFF).toByte(), 0x20))) {
                    log("Address set fail @ $off"); return false
                }
                val page = hex.copyOfRange(off, off + PAGE)
                val pkt = ByteArray(4 + PAGE + 1)
                pkt[0] = 0x64
                pkt[1] = 0x00
                pkt[2] = PAGE.toByte()
                pkt[3] = 'F'.code.toByte()
                System.arraycopy(page, 0, pkt, 4, PAGE)
                pkt[4 + PAGE] = 0x20
                if (!cmd(p, pkt)) { log("Page write fail @ $off"); return false }
                off += PAGE
                progress(off.toFloat() / hex.size)
            }
            cmd(p, byteArrayOf(0x51, 0x20))
            log("Upload complete: ${hex.size} bytes")
            return true
        } catch (e: Exception) {
            log("Upload error: ${e.message}")
            return false
        } finally {
            try { link.setBaud(oldBaud) } catch (_: Exception) {}
            link.resume()
        }
    }
}
