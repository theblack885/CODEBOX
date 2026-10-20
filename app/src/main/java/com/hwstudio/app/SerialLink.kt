package com.hwstudio.app

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import androidx.core.content.ContextCompat
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager

/** USB OTG serial connection (Arduino, ESP32, CH340, CP210x, FTDI ...) */
class SerialLink(private val ctx: Context, private val onLine: (String) -> Unit) {

    private val usb = ctx.getSystemService(Context.USB_SERVICE) as UsbManager
    var port: UsbSerialPort? = null
        private set
    private var mgr: SerialInputOutputManager? = null
    private val buf = StringBuilder()
    private var baud = 115200

    val isOpen: Boolean get() = port != null

    fun list(): List<UsbSerialDriver> = UsbSerialProber.getDefaultProber().findAllDrivers(usb)

    fun connect(driver: UsbSerialDriver, baudRate: Int, cb: (Boolean, String) -> Unit) {
        val dev = driver.device
        if (usb.hasPermission(dev)) {
            val r = open(driver, baudRate)
            cb(r.first, r.second)
            return
        }
        val action = ctx.packageName + ".USB_PERMISSION"
        val pi = PendingIntent.getBroadcast(
            ctx, 0, Intent(action).setPackage(ctx.packageName), PendingIntent.FLAG_MUTABLE
        )
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                try { ctx.unregisterReceiver(this) } catch (_: Exception) {}
                if (i.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                    val r = open(driver, baudRate)
                    cb(r.first, r.second)
                } else cb(false, "USB permission deny hui")
            }
        }
        ContextCompat.registerReceiver(ctx, receiver, IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED)
        usb.requestPermission(dev, pi)
    }

    private fun open(driver: UsbSerialDriver, baudRate: Int): Pair<Boolean, String> {
        return try {
            val conn = usb.openDevice(driver.device) ?: return Pair(false, "Device open nahi hua")
            val p = driver.ports[0]
            p.open(conn)
            p.setParameters(baudRate, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            p.dtr = true
            p.rts = true
            port = p
            baud = baudRate
            startIo()
            Pair(true, "Connected @ $baudRate")
        } catch (e: Exception) {
            Pair(false, "Error: ${e.message}")
        }
    }

    private fun startIo() {
        val p = port ?: return
        val m = SerialInputOutputManager(p, object : SerialInputOutputManager.Listener {
            override fun onNewData(data: ByteArray) {
                val s = String(data, Charsets.UTF_8)
                for (ch in s) {
                    if (ch == '\n') {
                        onLine(buf.toString().trimEnd('\r'))
                        buf.setLength(0)
                    } else buf.append(ch)
                }
            }
            override fun onRunError(e: Exception) {
                onLine("[serial error] ${e.message}")
            }
        })
        mgr = m
        m.start()
    }

    /** Upload ke dauran background reader band karna padta hai */
    fun pause() { mgr?.stop(); mgr = null }
    fun resume() { if (port != null && mgr == null) startIo() }

    fun setBaud(b: Int) {
        port?.setParameters(b, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
        baud = b
    }

    fun currentBaud() = baud

    fun write(bytes: ByteArray) {
        try { port?.write(bytes, 1000) } catch (e: Exception) { onLine("[write error] ${e.message}") }
    }

    fun writeLine(s: String) = write((s + "\n").toByteArray())

    fun close() {
        pause()
        try { port?.close() } catch (_: Exception) {}
        port = null
    }
}
