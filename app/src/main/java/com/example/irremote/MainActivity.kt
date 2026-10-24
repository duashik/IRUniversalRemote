package com.example.irremote

import android.content.Context
import android.content.SharedPreferences
import android.hardware.ConsumerIrManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private var ir: ConsumerIrManager? = null
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var prefs: SharedPreferences
    private lateinit var status: TextView

    private val ADDR = 0x08
    private var scanKey: String? = null
    private var cmd = -1
    private var scanning = false

    private val keys = listOf(
        "power" to "Power", "volup" to "Vol +", "voldown" to "Vol −",
        "chup" to "CH +", "chdown" to "CH −", "mute" to "Mute",
        "input" to "Input/AV", "menu" to "Menu",
        "up" to "ওপর", "down" to "নিচে", "left" to "বামে",
        "right" to "ডানে", "ok" to "OK"
    )

    private val stepRunnable = Runnable { step() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ir = getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
        prefs = getSharedPreferences("codes", Context.MODE_PRIVATE)

        // Power কোড ঠিক করা (পুরনো ভুল 0x08 মুছে 0x12 বসায়)
        if (!prefs.getBoolean("fix_v2", false)) {
            prefs.edit().putInt("power", 0x12).putBoolean("fix_v2", true).apply()
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        val hasIr = ir?.hasIrEmitter() == true
        status = TextView(this).apply {
            textSize = 17f
            gravity = Gravity.CENTER
            text = if (hasIr) "IR ব্লাস্টার ✔  (address 0x08)" else "IR ব্লাস্টার পাওয়া যায়নি ✘"
        }
        root.addView(status)

        val ctl1 = row()
        ctl1.addView(btn("✔ এটাই") { yes() })
        ctl1.addView(btn("থামান") { stop(); status.text = "থামানো (কমান্ড ${hex(cmd)})" })
        val ctl2 = row()
        ctl2.addView(btn("◀ পিছনে") { back() })
        ctl2.addView(btn("আবার") { again() })
        ctl2.addView(btn("পরের ▶") { next() })
        root.addView(ctl1); root.addView(ctl2)

        for ((k, label) in keys) {
            val r = row()
            r.addView(btn(label, 2f) { send(k) })
            r.addView(btn("স্ক্যান", 1f) { startScan(k, label) })
            root.addView(r)
        }

        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun row() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
    }

    private fun btn(t: String, w: Float = 1f, onClick: () -> Unit) = Button(this).apply {
        text = t
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, w)
        setOnClickListener { onClick() }
    }

    private fun hex(c: Int) = if (c < 0) "-" else "0x%02X".format(c)

    private fun send(key: String) {
        stop()
        val c = prefs.getInt(key, -1)
        if (c < 0) { status.text = "এই বাটনের কোড নেই, 'স্ক্যান' চাপুন"; return }
        sendNec(ADDR, c)
        status.text = "পাঠানো: $key = ${hex(c)}"
    }

    private fun startScan(key: String, label: String) {
        stop()
        scanKey = key
        cmd = -1
        scanning = true
        status.text = "$label স্ক্যান শুরু..."
        step()
    }

    private fun step() {
        if (!scanning) return
        cmd++
        if (cmd > 255) { scanning = false; status.text = "স্ক্যান শেষ, মেলেনি"; return }
        sendNec(ADDR, cmd)
        status.text = "[${scanKey}] পাঠানো: ${hex(cmd)} / 0xFF\nটিভিতে কাজ হলে 'এটাই' চাপুন"
        handler.postDelayed(stepRunnable, 1200)
    }

    private fun stop() {
        scanning = false
        handler.removeCallbacks(stepRunnable)
    }

    private fun yes() {
        stop()
        val k = scanKey
        if (k == null || cmd < 0) { status.text = "আগে স্ক্যান শুরু করুন"; return }
        prefs.edit().putInt(k, cmd).apply()
        status.text = "সেভ হয়েছে: $k = ${hex(cmd)}"
    }

    private fun back() {
        stop()
        if (cmd > 0) cmd--
        if (cmd >= 0) { sendNec(ADDR, cmd); status.text = "[${scanKey}] আবার: ${hex(cmd)}" }
    }

    private fun again() {
        stop()
        if (cmd >= 0) { sendNec(ADDR, cmd); status.text = "[${scanKey}] আবার: ${hex(cmd)}" }
    }

    private fun next() {
        stop()
        if (cmd < 255) cmd++
        sendNec(ADDR, cmd)
        status.text = "[${scanKey}] পরের: ${hex(cmd)}"
    }

    private fun sendNec(addr: Int, c: Int) {
        val p = ArrayList<Int>()
        p.add(9000); p.add(4500)
        val bytes = listOf(addr, addr.inv() and 0xFF, c, c.inv() and 0xFF)
        for (b in bytes) for (i in 0 until 8) {
            p.add(560)
            p.add(if ((b shr i) and 1 == 1) 1690 else 560)
        }
        p.add(560)
        ir?.transmit(38000, p.toIntArray())
    }

    override fun onDestroy() {
        stop()
        super.onDestroy()
    }
}
