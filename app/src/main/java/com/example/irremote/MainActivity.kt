package com.example.irremote

import android.content.Context
import android.hardware.ConsumerIrManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private var ir: ConsumerIrManager? = null
    private val handler = Handler(Looper.getMainLooper())
    private var scanning = false
    private var index = 0
    private lateinit var status: TextView

    // NEC address list (common local/Chinese TV addresses)
    private val addresses = listOf(0x00, 0x01, 0x02, 0x04, 0x08, 0x10, 0x20, 0x40, 0x7F, 0x80, 0xBF, 0xDF, 0xFE)
    // Common Power command codes
    private val commands = listOf(0x12, 0x08, 0x0C, 0x02, 0x1C, 0x40, 0x00, 0x15, 0x45, 0x46, 0x47, 0x10)

    private val combos: List<Pair<Int, Int>> =
        addresses.flatMap { a -> commands.map { c -> Pair(a, c) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ir = getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
        }
        status = TextView(this).apply { textSize = 18f; gravity = Gravity.CENTER }
        val start = Button(this).apply { text = "স্ক্যান শুরু" }
        val worked = Button(this).apply { text = "টিভি বন্ধ হয়েছে!" }
        val stop = Button(this).apply { text = "থামান" }
        root.addView(status); root.addView(start); root.addView(worked); root.addView(stop)
        setContentView(root)

        val hasIr = ir?.hasIrEmitter() == true
        status.text = if (hasIr) "IR ব্লাস্টার পাওয়া গেছে ✔" else "IR ব্লাস্টার পাওয়া যায়নি ✘"

        start.setOnClickListener { if (hasIr) { scanning = true; index = 0; step() } }
        stop.setOnClickListener { scanning = false; status.text = "থামানো হয়েছে (কোড #$index)" }
        worked.setOnClickListener {
            scanning = false
            val p = combos.getOrNull(maxOf(index - 1, 0))
            if (p != null) status.text = "সফল কোড: NEC address=0x%02X, command=0x%02X".format(p.first, p.second)
        }
    }

    private fun step() {
        if (!scanning) return
        if (index >= combos.size) { scanning = false; status.text = "স্ক্যান শেষ, কোড মেলেনি"; return }
        val (a, c) = combos[index]
        sendNec(a, c)
        index++
        status.text = "পাঠানো হচ্ছে ${index}/${combos.size}\naddress=0x%02X command=0x%02X".format(a, c)
        handler.postDelayed({ step() }, 1500)
    }

    private fun sendNec(addr: Int, cmd: Int) {
        val p = ArrayList<Int>()
        p.add(9000); p.add(4500)
        val bytes = listOf(addr, addr.inv() and 0xFF, cmd, cmd.inv() and 0xFF)
        for (b in bytes) for (i in 0 until 8) {
            p.add(560)
            p.add(if ((b shr i) and 1 == 1) 1690 else 560)
        }
        p.add(560)
        ir?.transmit(38000, p.toIntArray())
    }
}
