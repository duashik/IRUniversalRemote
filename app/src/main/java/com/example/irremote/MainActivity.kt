package com.example.irremote

import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.hardware.ConsumerIrManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SwitchCompat
import org.json.JSONArray
import org.json.JSONObject

class Btn(var name: String, var cmd: Int, var id: String = "")
class Chan(var name: String, var num: String)
class Profile(var name: String, var addr: Int) {
    val buttons = ArrayList<Btn>()
    val digits = IntArray(10) { -1 }
    val channels = ArrayList<Chan>()
}

class MainActivity : AppCompatActivity() {

    private var ir: ConsumerIrManager? = null
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var prefs: SharedPreferences
    private val profiles = ArrayList<Profile>()
    private var cur = 0
    private var tab = 0
    private var edit = false
    private var dark = false
    private var vib = true
    private var lang = "bn"
    private var scanDelay = 1200
    private var digitDelay = 500
    private var statusMsg = ""
    private val chanInput = StringBuilder()
    private lateinit var statusTv: TextView
    private lateinit var chanDisplay: TextView
    private var sv: ScrollView? = null

    private val MP = ViewGroup.LayoutParams.MATCH_PARENT
    private val WC = ViewGroup.LayoutParams.WRAP_CONTENT
    private val accent = Color.parseColor("#4F7CFF")
    private val danger = Color.parseColor("#E5484D")

    // id, বাংলা, English
    private val defKeys = listOf(
        Triple("power", "Power", "Power"),
        Triple("volup", "Vol +", "Vol +"),
        Triple("voldown", "Vol −", "Vol −"),
        Triple("chup", "CH +", "CH +"),
        Triple("chdown", "CH −", "CH −"),
        Triple("mute", "Mute", "Mute"),
        Triple("input", "Input/AV", "Input/AV"),
        Triple("menu", "Menu", "Menu"),
        Triple("up", "ওপর", "Up"),
        Triple("down", "নিচে", "Down"),
        Triple("left", "বামে", "Left"),
        Triple("right", "ডানে", "Right"),
        Triple("ok", "OK", "OK")
    )

    // ---------- ভাষা / Language ----------
    private fun t(bn: String, en: String) = if (lang == "bn") bn else en

    private fun dispName(b: Btn): String {
        val d = defKeys.firstOrNull { it.first == b.id } ?: return b.name
        return if (b.name == d.second || b.name == d.third) t(d.second, d.third) else b.name
    }

    // ---------- রং ----------
    private fun bg() = Color.parseColor(if (dark) "#121417" else "#F2F4F8")
    private fun card() = Color.parseColor(if (dark) "#1E2228" else "#FFFFFF")
    private fun dim() = Color.parseColor(if (dark) "#2A2F37" else "#E4E8F0")
    private fun line() = Color.parseColor(if (dark) "#2B313A" else "#DDE2EC")
    private fun txt() = Color.parseColor(if (dark) "#ECEFF4" else "#1B1F27")
    private fun sub() = Color.parseColor(if (dark) "#9AA3B2" else "#6B7385")
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    // ---------- জীবনচক্র ----------
    override fun onCreate(savedInstanceState: Bundle?) {
        val sp = getSharedPreferences("irapp", Context.MODE_PRIVATE)
        if (sp.contains("night")) {
            AppCompatDelegate.setDefaultNightMode(
                if (sp.getBoolean("night", false)) AppCompatDelegate.MODE_NIGHT_YES
                else AppCompatDelegate.MODE_NIGHT_NO
            )
        }
        super.onCreate(savedInstanceState)
        prefs = sp
        supportActionBar?.hide()
        ir = getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
        lang = prefs.getString("lang", "bn") ?: "bn"
        vib = prefs.getBoolean("vib", true)
        scanDelay = prefs.getInt("scanDelay", 1200)
        digitDelay = prefs.getInt("digitDelay", 500)
        dark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        load()
        statusMsg = if (ir?.hasIrEmitter() == true) t("IR ব্লাস্টার ✔", "IR blaster ✔")
        else t("IR ব্লাস্টার পাওয়া যায়নি ✘", "No IR blaster found ✘")
        render(false)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    // ---------- ডেটা ----------
    private fun prof() = profiles[cur]

    private fun save() {
        val arr = JSONArray()
        for (p in profiles) {
            val o = JSONObject()
            o.put("name", p.name)
            o.put("addr", p.addr)
            val b = JSONArray()
            for (x in p.buttons) b.put(JSONObject().put("n", x.name).put("c", x.cmd).put("i", x.id))
            o.put("buttons", b)
            val d = JSONArray()
            for (x in p.digits) d.put(x)
            o.put("digits", d)
            val c = JSONArray()
            for (x in p.channels) c.put(JSONObject().put("n", x.name).put("u", x.num))
            o.put("channels", c)
            arr.put(o)
        }
        prefs.edit().putString("profiles", arr.toString()).putInt("cur", cur).apply()
    }

    private fun load() {
        profiles.clear()
        val s = prefs.getString("profiles", null)
        if (s != null) {
            try {
                val arr = JSONArray(s)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val p = Profile(o.getString("name"), o.getInt("addr"))
                    val b = o.getJSONArray("buttons")
                    for (j in 0 until b.length()) {
                        val x = b.getJSONObject(j)
                        val nm = x.getString("n")
                        var id = x.optString("i", "")
                        if (id.isEmpty()) {
                            id = defKeys.firstOrNull { it.second == nm || it.third == nm }?.first ?: ""
                        }
                        p.buttons.add(Btn(nm, x.getInt("c"), id))
                    }
                    val d = o.getJSONArray("digits")
                    for (j in 0 until minOf(10, d.length())) p.digits[j] = d.getInt(j)
                    val c = o.getJSONArray("channels")
                    for (j in 0 until c.length()) {
                        val x = c.getJSONObject(j)
                        p.channels.add(Chan(x.getString("n"), x.getString("u")))
                    }
                    profiles.add(p)
                }
            } catch (e: Exception) {
                profiles.clear()
            }
        }
        if (profiles.isEmpty()) {
            val old = getSharedPreferences("codes", Context.MODE_PRIVATE)
            val p = Profile(t("আমার টিভি", "My TV"), 0x08)
            for ((k, bn, _) in defKeys) {
                p.buttons.add(Btn(bn, if (k == "power") 0x12 else old.getInt(k, -1), k))
            }
            profiles.add(p)
            cur = 0
            save()
        }
        cur = prefs.getInt("cur", 0).coerceIn(0, profiles.size - 1)
    }

    // ---------- IR ----------
    private fun hex(c: Int) = if (c < 0) "—" else "0x%02X".format(c)

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

    // ---------- UI সহায়ক ----------
    private fun round(color: Int, r: Int = 14, stroke: Boolean = true) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(r).toFloat()
        if (stroke) setStroke(dp(1), line())
    }

    private fun pill(t: String, bgc: Int, fg: Int, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = t
        b.isAllCaps = false
        b.setTextColor(fg)
        b.textSize = 15f
        b.background = RippleDrawable(
            ColorStateList.valueOf(Color.parseColor("#33888888")),
            round(bgc), round(Color.WHITE, 14, false)
        )
        b.stateListAnimator = null
        b.minHeight = dp(46); b.minimumHeight = dp(46)
        b.minWidth = 0; b.minimumWidth = 0
        b.setOnClickListener {
            if (vib) it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            onClick()
        }
        return b
    }

    private fun small(t: String, onClick: () -> Unit): Button {
        val b = pill(t, dim(), txt(), onClick)
        b.minHeight = dp(40); b.minimumHeight = dp(40)
        return b
    }

    private fun hrow() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

    private fun LinearLayout.addW(v: View, w: Float = 1f) {
        val lp = LinearLayout.LayoutParams(if (w > 0f) 0 else WC, WC, w)
        lp.setMargins(dp(3), dp(3), dp(3), dp(3))
        addView(v, lp)
    }

    private fun LinearLayout.addV(v: View) {
        val lp = LinearLayout.LayoutParams(MP, WC)
        lp.setMargins(dp(3), dp(4), dp(3), dp(4))
        addView(v, lp)
    }

    private fun sec(t: String) = TextView(this).apply {
        text = t
        textSize = 13f
        setTextColor(sub())
        typeface = Typeface.DEFAULT_BOLD
        setPadding(dp(6), dp(14), dp(6), dp(4))
    }

    private fun label(t: String) = TextView(this).apply {
        text = t
        textSize = 12f
        setPadding(0, dp(10), 0, 0)
    }

    private fun field(h: String, v: String, numeric: Boolean = false) = EditText(this).apply {
        hint = h
        setText(v)
        setSingleLine()
        if (numeric) inputType = InputType.TYPE_CLASS_NUMBER
    }

    private fun vbox(vararg vs: View) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(8), dp(20), 0)
        for (v in vs) addView(v)
    }

    private fun confirm(m: String, ok: () -> Unit) {
        AlertDialog.Builder(this).setMessage(m)
            .setPositiveButton(t("হ্যাঁ", "Yes")) { _, _ -> ok() }
            .setNegativeButton(t("না", "No"), null).show()
    }

    private fun msg(s: String) {
        statusMsg = s
        statusTv.text = s
    }

    private fun <T> move(l: MutableList<T>, i: Int, d: Int) {
        val j = i + d
        if (j in 0 until l.size) {
            val x = l[i]; l[i] = l[j]; l[j] = x
            save(); render()
        }
    }

    // ---------- মূল স্ক্রিন ----------
    private fun render(keepPos: Boolean = true) {
        val keep = if (keepPos) (sv?.scrollY ?: 0) else 0
        val p = prof()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg())
            setPadding(dp(10), dp(6), dp(10), dp(6))
        }

        val top = hrow()
        val pn = pill("📺 ${p.name} ▾", card(), txt()) { profileMenu() }
        pn.setSingleLine()
        top.addW(pn, 1f)
        top.addW(
            pill(
                if (edit) t("✔ শেষ", "✔ Done") else t("✎ এডিট", "✎ Edit"),
                if (edit) accent else card(),
                if (edit) Color.WHITE else txt()
            ) { edit = !edit; render(false) }, 0f
        )
        top.addW(pill("⚙", card(), txt()) { settings() }, 0f)
        root.addView(top)

        val tabs = hrow()
        tabs.addW(pill(t("🎛 রিমোট", "🎛 Remote"), if (tab == 0) accent else card(),
            if (tab == 0) Color.WHITE else txt()) { tab = 0; render(false) })
        tabs.addW(pill(t("🔢 চ্যানেল", "🔢 Channels"), if (tab == 1) accent else card(),
            if (tab == 1) Color.WHITE else txt()) { tab = 1; render(false) })
        root.addView(tabs)

        statusTv = TextView(this).apply {
            text = statusMsg
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(sub())
            setPadding(dp(8), dp(4), dp(8), dp(6))
        }
        root.addView(statusTv)

        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this)
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(MP, 0, 1f))
        sv = scroll

        if (tab == 0) buildRemote(body) else buildChannels(body)

        setContentView(root)
        scroll.post { scroll.scrollTo(0, keep) }
    }

    // ---------- রিমোট ট্যাব ----------
    private fun buildRemote(body: LinearLayout) {
        val p = prof()
        val bs = p.buttons
        if (!edit) {
            var i = 0
            while (i < bs.size) {
                val r = hrow()
                for (k in 0 until 3) {
                    if (i + k < bs.size) r.addW(keyBtn(bs[i + k])) else r.addW(View(this))
                }
                body.addView(r)
                i += 3
            }
            if (bs.isEmpty()) body.addView(sec(t("কোনো বাটন নেই। ✎ এডিট চেপে যোগ করুন।",
                "No buttons. Tap ✎ Edit to add some.")))
            body.addView(sec(t("💡 কোড না থাকলে বাটন চাপুন বা চেপে ধরুন, তাহলে স্ক্যান খুলবে",
                "💡 If a button has no code, tap it (or long-press) to open the scanner")))
        } else {
            for ((idx, b) in bs.withIndex()) {
                body.addV(editCard(
                    "${dispName(b)}   •   ${hex(b.cmd)}",
                    { move(bs, idx, -1) }, { move(bs, idx, 1) },
                    { editBtn(b) }, { scanFor(b) },
                    {
                        confirm(t("\"${dispName(b)}\" মুছবেন?", "Delete \"${dispName(b)}\"?")) {
                            bs.removeAt(idx); save(); render()
                        }
                    }
                ))
            }
            body.addV(pill(t("＋ নতুন বাটন", "＋ New button"), accent, Color.WHITE) { addBtn() })
        }
    }

    private fun keyBtn(b: Btn): Button {
        val set = b.cmd >= 0
        val nm = dispName(b)
        val fg = if (b.id == "power") danger else if (set) txt() else sub()
        val bt = pill(if (set) nm else "$nm\n" + t("＋ সেট", "＋ Set"),
            if (set) card() else dim(), fg) { pressKey(b) }
        bt.minHeight = dp(64); bt.minimumHeight = dp(64)
        bt.textSize = 16f
        bt.setOnLongClickListener { scanFor(b); true }
        return bt
    }

    private fun pressKey(b: Btn) {
        if (b.cmd < 0) { scanFor(b); return }
        sendNec(prof().addr, b.cmd)
        msg("▶ ${dispName(b)}  (${hex(b.cmd)})")
    }

    private fun scanFor(b: Btn) {
        openScan("${dispName(b)} — " + t("কোড খুঁজুন", "Find code"), b.cmd) {
            b.cmd = it
            save()
            render()
            msg(t("সেভ হয়েছে: ", "Saved: ") + "${dispName(b)} = ${hex(it)}")
        }
    }

    private fun editCard(
        text: String, onUp: () -> Unit, onDown: () -> Unit, onEdit: () -> Unit,
        onScan: (() -> Unit)?, onDel: () -> Unit
    ): View {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.background = round(card())
        c.setPadding(dp(12), dp(10), dp(12), dp(6))
        val tv = TextView(this)
        tv.text = text
        tv.textSize = 16f
        tv.setTextColor(txt())
        tv.typeface = Typeface.DEFAULT_BOLD
        c.addView(tv)
        val r = hrow()
        r.addW(small("▲", onUp))
        r.addW(small("▼", onDown))
        r.addW(small("✎", onEdit))
        if (onScan != null) r.addW(small("🔍", onScan))
        val d = small("✕", onDel)
        d.setTextColor(danger)
        r.addW(d)
        c.addView(r)
        return c
    }

    private fun editBtn(b: Btn) {
        val n = field(t("নাম", "Name"), dispName(b))
        val h = field(t("কমান্ড হেক্স (ফাঁকা = সেট নেই)", "Command hex (empty = not set)"),
            if (b.cmd >= 0) "%02X".format(b.cmd) else "")
        AlertDialog.Builder(this).setTitle(t("বাটন এডিট", "Edit button")).setView(vbox(n, h))
            .setPositiveButton(t("সেভ", "Save")) { _, _ ->
                val nm = n.text.toString().trim()
                val hs = h.text.toString().trim()
                val cv = if (hs.isEmpty()) -1 else (hs.toIntOrNull(16) ?: -2)
                if (nm.isEmpty() || cv < -1 || cv > 255) {
                    msg(t("নাম বা কোড ঠিক নেই", "Invalid name or code"))
                    return@setPositiveButton
                }
                if (nm != dispName(b)) b.name = nm
                b.cmd = cv
                save(); render()
            }.setNegativeButton(t("বাতিল", "Cancel"), null).show()
    }

    private fun addBtn() {
        val n = field(t("বাটনের নাম", "Button name"), "")
        val h = field(t("কমান্ড হেক্স (ফাঁকা = স্ক্যান করে নেব)", "Command hex (empty = scan for it)"), "")
        AlertDialog.Builder(this).setTitle(t("নতুন বাটন", "New button")).setView(vbox(n, h))
            .setPositiveButton(t("যোগ", "Add")) { _, _ ->
                val nm = n.text.toString().trim()
                val hs = h.text.toString().trim()
                val cv = if (hs.isEmpty()) -1 else (hs.toIntOrNull(16) ?: -2)
                if (nm.isEmpty() || cv < -1 || cv > 255) {
                    msg(t("নাম বা কোড ঠিক নেই", "Invalid name or code"))
                    return@setPositiveButton
                }
                val nb = Btn(nm, cv)
                prof().buttons.add(nb)
                save(); render()
                if (cv < 0) scanFor(nb)
            }.setNegativeButton(t("বাতিল", "Cancel"), null).show()
    }

    // ---------- চ্যানেল ট্যাব ----------
    private fun buildChannels(body: LinearLayout) {
        val p = prof()

        chanDisplay = TextView(this).apply {
            text = if (chanInput.isEmpty()) "—" else chanInput.toString()
            textSize = 36f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(txt())
            background = round(card())
            setPadding(dp(8), dp(14), dp(8), dp(14))
        }
        body.addV(chanDisplay)

        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "⌫", "0", "GO")
        for (ri in 0 until 4) {
            val r = hrow()
            for (k in 0 until 3) {
                val key = keys[ri * 3 + k]
                val go = key == "GO"
                val b = pill(if (go) t("▶ পাঠান", "▶ Send") else key,
                    if (go) accent else card(), if (go) Color.WHITE else txt()) { onKey(key) }
                b.textSize = 20f
                b.minHeight = dp(56); b.minimumHeight = dp(56)
                r.addW(b)
            }
            body.addView(r)
        }

        val r2 = hrow()
        r2.addW(pill(t("⚙ ডিজিট কোড সেট", "⚙ Set digit codes"), dim(), txt()) { digitsDialog() })
        r2.addW(pill(t("＋ চ্যানেল সেভ", "＋ Save channel"), dim(), txt()) { chanDialog(null) })
        body.addView(r2)

        body.addView(sec(t("📺 চ্যানেল তালিকা", "📺 Channel list")))
        if (p.channels.isEmpty()) body.addView(sec(t("কোনো চ্যানেল নেই। ＋ চ্যানেল সেভ চাপুন।",
            "No channels. Tap ＋ Save channel.")))
        val cs = p.channels
        for ((idx, c) in cs.withIndex()) {
            if (!edit) {
                val b = pill("${c.name}    •    ${c.num}", card(), txt()) {
                    chanInput.setLength(0)
                    chanInput.append(c.num)
                    chanDisplay.text = c.num
                    sendDigits(c.num)
                }
                b.gravity = Gravity.START or Gravity.CENTER_VERTICAL
                body.addV(b)
            } else {
                body.addV(editCard(
                    "${c.name}   •   ${c.num}",
                    { move(cs, idx, -1) }, { move(cs, idx, 1) },
                    { chanDialog(c) }, null,
                    {
                        confirm(t("\"${c.name}\" মুছবেন?", "Delete \"${c.name}\"?")) {
                            cs.removeAt(idx); save(); render()
                        }
                    }
                ))
            }
        }
    }

    private fun onKey(k: String) {
        when (k) {
            "⌫" -> if (chanInput.isNotEmpty()) chanInput.setLength(chanInput.length - 1)
            "GO" -> if (chanInput.isNotEmpty()) sendDigits(chanInput.toString())
            else -> if (chanInput.length < 4) chanInput.append(k)
        }
        chanDisplay.text = if (chanInput.isEmpty()) "—" else chanInput.toString()
    }

    private fun sendDigits(num: String) {
        val p = prof()
        val chars = num.filter { it.isDigit() }
        if (chars.isEmpty()) return
        val missing = chars.map { it - '0' }.filter { p.digits[it] < 0 }.toSet()
        if (missing.isNotEmpty()) {
            msg(t("ডিজিট কোড নেই: ", "Missing digit codes: ") + missing.sorted().joinToString(",") +
                t(" — '⚙ ডিজিট কোড সেট' চাপুন", " — tap '⚙ Set digit codes'"))
            return
        }
        var i = 0
        msg(t("চ্যানেল $num পাঠানো হচ্ছে...", "Sending channel $num..."))
        val r = object : Runnable {
            override fun run() {
                if (i < chars.length) {
                    sendNec(p.addr, p.digits[chars[i] - '0'])
                    i++
                    handler.postDelayed(this, digitDelay.toLong())
                } else {
                    msg(t("চ্যানেল $num পাঠানো হয়েছে ✔", "Channel $num sent ✔"))
                }
            }
        }
        handler.post(r)
    }

    private fun digitsDialog() {
        val p = prof()
        val box = vbox()
        val btns = ArrayList<Button>()
        fun lb(d: Int) = if (p.digits[d] >= 0) "$d ✔" else "$d ✘"
        for (start in listOf(0, 5)) {
            val r = hrow()
            for (d in start until start + 5) {
                val b = pill(lb(d), dim(), txt()) {
                    openScan(t("ডিজিট $d", "Digit $d"), p.digits[d]) { v ->
                        p.digits[d] = v
                        save()
                        btns[d].text = lb(d)
                    }
                }
                btns.add(b)
                r.addW(b)
            }
            box.addView(r)
        }
        AlertDialog.Builder(this).setTitle(t("ডিজিট কোড (০–৯)", "Digit codes (0–9)"))
            .setMessage(t("যেটা সেট করবেন সেটা চাপুন, তারপর স্ক্যান করুন।",
                "Tap the digit you want to set, then scan."))
            .setView(box)
            .setPositiveButton(t("ঠিক আছে", "OK"), null).show()
    }

    private fun chanDialog(c: Chan?) {
        val n = field(t("চ্যানেলের নাম", "Channel name"), c?.name ?: "")
        val u = field(t("চ্যানেল নম্বর", "Channel number"), c?.num ?: chanInput.toString(), true)
        AlertDialog.Builder(this)
            .setTitle(if (c == null) t("নতুন চ্যানেল", "New channel") else t("চ্যানেল এডিট", "Edit channel"))
            .setView(vbox(n, u))
            .setPositiveButton(t("সেভ", "Save")) { _, _ ->
                val nm = n.text.toString().trim()
                val nu = u.text.toString().trim()
                if (nm.isEmpty() || nu.isEmpty()) {
                    msg(t("নাম ও নম্বর দিন", "Enter name and number"))
                    return@setPositiveButton
                }
                if (c == null) prof().channels.add(Chan(nm, nu)) else { c.name = nm; c.num = nu }
                save(); render()
            }.setNegativeButton(t("বাতিল", "Cancel"), null).show()
    }

    // ---------- স্ক্যান ডায়লগ ----------
    private fun openScan(title: String, start: Int, onSave: (Int) -> Unit) {
        val p = prof()
        var c = start
        var auto = false
        val h = Handler(Looper.getMainLooper())

        val info = TextView(this).apply {
            textSize = 24f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(10), 0, dp(4))
        }
        val hint = TextView(this).apply {
            textSize = 13f
            gravity = Gravity.CENTER
            text = t("টিভিতে কাজ হলে ✔ এটাই চাপুন", "Press ✔ This one when the TV responds")
        }
        fun refresh() {
            info.text = if (c < 0) t("কমান্ড: —", "Command: —")
            else t("কমান্ড: ", "Command: ") + "0x%02X  (%d)".format(c, c)
        }
        fun fire() {
            if (c in 0..255) sendNec(p.addr, c)
            refresh()
        }
        val runner = object : Runnable {
            override fun run() {
                if (!auto) return
                if (c >= 255) {
                    auto = false
                    hint.text = t("স্ক্যান শেষ, মেলেনি", "Scan finished, no match")
                    return
                }
                c++
                fire()
                h.postDelayed(this, scanDelay.toLong())
            }
        }
        lateinit var autoBtn: Button
        fun stopAuto() {
            auto = false
            h.removeCallbacks(runner)
            autoBtn.text = t("▶▶ অটো", "▶▶ Auto")
        }
        lateinit var dlg: AlertDialog

        val back = pill(t("◀ পিছনে", "◀ Back"), dim(), txt()) {
            stopAuto()
            if (c > 0) c-- else if (c < 0) c = 0
            fire()
        }
        val again = pill(t("আবার", "Again"), dim(), txt()) { stopAuto(); fire() }
        val next = pill(t("পরের ▶", "Next ▶"), dim(), txt()) {
            stopAuto()
            if (c < 255) c++
            fire()
        }
        autoBtn = pill(t("▶▶ অটো", "▶▶ Auto"), dim(), txt()) {
            if (auto) {
                stopAuto()
            } else {
                auto = true
                autoBtn.text = t("⏸ থামান", "⏸ Stop")
                h.post(runner)
            }
        }
        val ok = pill(t("✔ এটাই", "✔ This one"), accent, Color.WHITE) {
            stopAuto()
            if (c in 0..255) {
                onSave(c)
                dlg.dismiss()
            }
        }
        val jump = field(t("হেক্স কোড, যেমন 1A", "Hex code, e.g. 1A"), "")
        val go = small(t("যাও", "Go")) {
            val v = jump.text.toString().trim().toIntOrNull(16)
            if (v != null && v in 0..255) { stopAuto(); c = v; fire() }
        }

        val r1 = hrow()
        r1.addW(back); r1.addW(again); r1.addW(next)
        val r2 = hrow()
        r2.addW(autoBtn); r2.addW(ok)
        val r3 = hrow()
        r3.addW(jump, 1f); r3.addW(go, 0f)

        val box = vbox(info, hint, r1, r2, r3)
        refresh()
        dlg = AlertDialog.Builder(this).setTitle(title).setView(box)
            .setNegativeButton(t("বন্ধ", "Close"), null).create()
        dlg.setOnDismissListener { auto = false; h.removeCallbacks(runner) }
        dlg.show()
    }

    // ---------- প্রোফাইল ----------
    private fun profileMenu() {
        val items = ArrayList<String>()
        for ((i, p) in profiles.withIndex()) items.add((if (i == cur) "● " else "○ ") + p.name)
        items.add(t("＋ নতুন প্রোফাইল", "＋ New profile"))
        items.add(t("⧉ এটি কপি করুন", "⧉ Duplicate this"))
        items.add(t("✎ নাম পরিবর্তন", "✎ Rename"))
        items.add(t("🗑 এটি মুছুন", "🗑 Delete this"))
        AlertDialog.Builder(this).setTitle(t("প্রোফাইল", "Profiles"))
            .setItems(items.toTypedArray()) { _, w ->
                val n = profiles.size
                when {
                    w < n -> { cur = w; save(); render(false) }
                    w == n -> newProfile()
                    w == n + 1 -> copyProfile()
                    w == n + 2 -> renameProfile()
                    else -> deleteProfile()
                }
            }.show()
    }

    private fun newProfile() {
        val n = field(t("প্রোফাইলের নাম (যেমন: বেডরুম টিভি)", "Profile name (e.g. Bedroom TV)"), "")
        val a = field(t("অ্যাড্রেস (হেক্স)", "Address (hex)"), "08")
        AlertDialog.Builder(this).setTitle(t("নতুন প্রোফাইল", "New profile")).setView(vbox(n, a))
            .setPositiveButton(t("তৈরি", "Create")) { _, _ ->
                val nm = n.text.toString().trim()
                val ad = a.text.toString().trim().toIntOrNull(16)
                if (nm.isEmpty() || ad == null || ad !in 0..255) {
                    msg(t("নাম বা অ্যাড্রেস ঠিক নেই", "Invalid name or address"))
                    return@setPositiveButton
                }
                val p = Profile(nm, ad)
                for ((k, bn, _) in defKeys) p.buttons.add(Btn(bn, -1, k))
                profiles.add(p)
                cur = profiles.size - 1
                save(); render(false)
            }.setNegativeButton(t("বাতিল", "Cancel"), null).show()
    }

    private fun copyProfile() {
        val o = prof()
        val p = Profile(o.name + t(" (কপি)", " (Copy)"), o.addr)
        for (b in o.buttons) p.buttons.add(Btn(b.name, b.cmd, b.id))
        for (i in 0 until 10) p.digits[i] = o.digits[i]
        for (c in o.channels) p.channels.add(Chan(c.name, c.num))
        profiles.add(p)
        cur = profiles.size - 1
        save(); render(false)
    }

    private fun renameProfile() {
        val n = field(t("নতুন নাম", "New name"), prof().name)
        AlertDialog.Builder(this).setTitle(t("নাম পরিবর্তন", "Rename")).setView(vbox(n))
            .setPositiveButton(t("সেভ", "Save")) { _, _ ->
                val nm = n.text.toString().trim()
                if (nm.isNotEmpty()) { prof().name = nm; save(); render() }
            }.setNegativeButton(t("বাতিল", "Cancel"), null).show()
    }

    private fun deleteProfile() {
        if (profiles.size <= 1) {
            msg(t("অন্তত একটি প্রোফাইল থাকতে হবে", "At least one profile is required"))
            return
        }
        confirm(t("\"${prof().name}\" প্রোফাইল মুছবেন?", "Delete profile \"${prof().name}\"?")) {
            profiles.removeAt(cur)
            cur = 0
            save(); render(false)
        }
    }

    // ---------- সেটিং ----------
    private fun settings() {
        val p = prof()

        val bnRb = RadioButton(this).apply { text = "বাংলা"; id = View.generateViewId() }
        val enRb = RadioButton(this).apply { text = "English"; id = View.generateViewId() }
        val rg = RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
            addView(bnRb); addView(enRb)
            check(if (lang == "bn") bnRb.id else enRb.id)
        }

        val nightSw = SwitchCompat(this).apply { text = t("🌙 রাত মোড", "🌙 Night mode"); isChecked = dark }
        val vibSw = SwitchCompat(this).apply { text = t("📳 বাটনে কম্পন", "📳 Vibrate on tap"); isChecked = vib }
        val d1 = field(t("মিলিসেকেন্ড", "Milliseconds"), scanDelay.toString(), true)
        val d2 = field(t("মিলিসেকেন্ড", "Milliseconds"), digitDelay.toString(), true)
        val ad = field(t("হেক্স", "Hex"), "%02X".format(p.addr))
        val box = vbox(
            label("🌐 ভাষা / Language"), rg,
            nightSw, vibSw,
            label(t("অটো স্ক্যানের বিরতি", "Auto-scan delay")), d1,
            label(t("চ্যানেলের ডিজিটের মাঝের বিরতি", "Delay between channel digits")), d2,
            label(t("অ্যাড্রেস — প্রোফাইল: ", "Address — profile: ") + p.name), ad
        )
        val scroll = ScrollView(this)
        scroll.addView(box)
        AlertDialog.Builder(this).setTitle(t("সেটিং", "Settings")).setView(scroll)
            .setPositiveButton(t("সেভ", "Save")) { _, _ ->
                lang = if (rg.checkedRadioButtonId == bnRb.id) "bn" else "en"
                vib = vibSw.isChecked
                scanDelay = (d1.text.toString().toIntOrNull() ?: 1200).coerceIn(300, 5000)
                digitDelay = (d2.text.toString().toIntOrNull() ?: 500).coerceIn(150, 3000)
                val a = ad.text.toString().trim().toIntOrNull(16)
                if (a != null && a in 0..255) p.addr = a
                prefs.edit().putString("lang", lang).putBoolean("vib", vib)
                    .putInt("scanDelay", scanDelay).putInt("digitDelay", digitDelay).apply()
                save()
                if (nightSw.isChecked != dark) {
                    prefs.edit().putBoolean("night", nightSw.isChecked).apply()
                    AppCompatDelegate.setDefaultNightMode(
                        if (nightSw.isChecked) AppCompatDelegate.MODE_NIGHT_YES
                        else AppCompatDelegate.MODE_NIGHT_NO
                    )
                } else {
                    render()
                }
            }.setNegativeButton(t("বাতিল", "Cancel"), null).show()
    }
}
