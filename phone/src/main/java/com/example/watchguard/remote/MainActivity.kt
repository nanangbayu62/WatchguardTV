package com.example.watchguard.remote

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class MainActivity : Activity() {

    private val main = Handler(Looper.getMainLooper())
    private lateinit var sp: SharedPreferences
    private var host: String? = null
    private var port = 8765

    private lateinit var statusText: TextView
    private lateinit var lockText: TextView
    private lateinit var quizText: TextView
    private lateinit var quizToggle: Button
    private lateinit var quizMinus: Button
    private lateinit var quizPlus: Button
    private lateinit var pinField: EditText
    private lateinit var ipField: EditText
    private lateinit var appsBox: LinearLayout

    private var nsd: NsdManager? = null
    private var discovery: NsdManager.DiscoveryListener? = null

    private val limits = listOf(1, 5, 15, 30, 45, 60, 90, 120, 180, 240)
    private val quizIntervals = listOf(1, 5, 10, 15, 20, 30)

    private val poll = object : Runnable {
        override fun run() {
            if (host != null) refresh()
            main.postDelayed(this, 10_000)
        }
    }

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()

    private fun tv(t: String, size: Float = 18f, bold: Boolean = false) = TextView(this).apply {
        text = t
        textSize = size
        setTextColor(Color.WHITE)
        if (bold) setTypeface(null, Typeface.BOLD)
        setPadding(0, dp(8), 0, dp(8))
    }

    private fun btn(t: String, onClick: () -> Unit) = Button(this).apply {
        text = t
        setOnClickListener { onClick() }
    }

    private fun check(t: String, checked: Boolean, onChange: (Boolean) -> Unit) = CheckBox(this).apply {
        text = t
        textSize = 16f
        setTextColor(Color.WHITE)
        isChecked = checked
        setOnCheckedChangeListener { _, c -> onChange(c) }
    }

    private fun weighted() = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

    private fun step(list: List<Int>, cur: Int, dir: Int): Int {
        val idx = list.indexOfFirst { it >= cur }.let { if (it < 0) list.lastIndex else it }
        return list[(idx + dir).coerceIn(0, list.lastIndex)]
    }

    private fun toast(m: String?) = Toast.makeText(this, m ?: "Error", Toast.LENGTH_SHORT).show()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sp = getSharedPreferences("remote", MODE_PRIVATE)
        host = sp.getString("host", null)
        port = sp.getInt("port", 8765)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(24))
            setBackgroundColor(0xFF101418.toInt())
        }
        root.addView(tv("WatchGuard Remote", 26f, true))
        statusText = tv("Mencari TV...", 16f)
        root.addView(statusText)

        pinField = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "PIN orang tua"
            textSize = 20f
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            setText(sp.getString("pin", ""))
        }
        root.addView(pinField)

        // --- Kunci ---
        lockText = tv("Status: -", 20f, true)
        root.addView(lockText)
        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row1.addView(btn("Kunci semua") { act("/lock") }, weighted())
        row1.addView(btn("Buka kunci") { act("/unlock") }, weighted())
        root.addView(row1)
        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row2.addView(btn("Segarkan") { refresh() }, weighted())
        row2.addView(btn("Reset waktu hari ini") {
            AlertDialog.Builder(this)
                .setMessage("Reset waktu tonton hari ini untuk semua aplikasi?")
                .setPositiveButton("Reset") { _, _ -> act("/reset") }
                .setNegativeButton("Batal", null)
                .show()
        }, weighted())
        root.addView(row2)

        // --- Kuis ---
        root.addView(tv("Kuis matematika (SD)", 20f, true))
        quizText = tv("Kuis: -", 16f)
        root.addView(quizText)
        val row3 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        quizToggle = btn("Nyalakan kuis") {}
        quizMinus = btn("- jeda") {}
        quizPlus = btn("+ jeda") {}
        row3.addView(quizToggle, weighted())
        row3.addView(quizMinus, weighted())
        row3.addView(quizPlus, weighted())
        root.addView(row3)

        // --- Aplikasi di TV ---
        root.addView(tv("Aplikasi di TV", 20f, true))
        root.addView(tv("Batas waktu dan kuis diatur terpisah untuk tiap aplikasi.", 14f))
        appsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(appsBox)

        // --- Manual ---
        root.addView(tv("Kalau TV tidak ditemukan otomatis, isi IP TV:", 14f))
        ipField = EditText(this).apply {
            hint = "contoh 192.168.1.5"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            setText(host ?: "")
        }
        root.addView(ipField)
        root.addView(btn("Hubungkan manual") {
            val h = ipField.text.toString().trim()
            if (h.isNotEmpty()) tryHost(h, 8765)
        })

        setContentView(ScrollView(this).apply {
            setBackgroundColor(0xFF101418.toInt())
            addView(root)
        })
    }

    override fun onStart() {
        super.onStart()
        startDiscovery()
        main.post(poll)
    }

    override fun onStop() {
        main.removeCallbacks(poll)
        stopDiscovery()
        super.onStop()
    }

    // ---------- Penemuan TV otomatis (mDNS/NSD) ----------

    private fun startDiscovery() {
        if (discovery != null) return
        val m = getSystemService(Context.NSD_SERVICE) as NsdManager
        nsd = m
        val l = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(t: String) {}
            override fun onServiceFound(i: NsdServiceInfo) {
                if (!i.serviceType.contains("_watchguard")) return
                try {
                    m.resolveService(i, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(s: NsdServiceInfo, e: Int) {}
                        override fun onServiceResolved(s: NsdServiceInfo) {
                            val a = s.host?.hostAddress ?: return
                            if (a.contains(":")) return
                            tryHost(a, s.port)
                        }
                    })
                } catch (_: Exception) {
                }
            }
            override fun onServiceLost(i: NsdServiceInfo) {}
            override fun onDiscoveryStopped(t: String) {}
            override fun onStartDiscoveryFailed(t: String, e: Int) {}
            override fun onStopDiscoveryFailed(t: String, e: Int) {}
        }
        discovery = l
        try {
            m.discoverServices("_watchguard._tcp", NsdManager.PROTOCOL_DNS_SD, l)
        } catch (e: Exception) {
            discovery = null
        }
    }

    private fun stopDiscovery() {
        discovery?.let { try { nsd?.stopServiceDiscovery(it) } catch (_: Exception) {} }
        discovery = null
    }

    private fun tryHost(h: String, p: Int) {
        Thread {
            try {
                val c = URL("http://$h:$p/ping").openConnection() as HttpURLConnection
                c.connectTimeout = 2500
                c.readTimeout = 2500
                val ok = c.responseCode == 200 &&
                    c.inputStream.bufferedReader().readText().contains("watchguard")
                if (ok) runOnUiThread {
                    host = h
                    port = p
                    sp.edit().putString("host", h).putInt("port", p).apply()
                    ipField.setText(h)
                    statusText.text = "Terhubung ke TV ($h)"
                    refresh()
                }
            } catch (_: Exception) {
            }
        }.start()
    }

    // ---------- Panggilan ke TV ----------

    private fun call(path: String, done: (JSONObject?, String?) -> Unit) {
        val h = host ?: return done(null, "TV belum ditemukan")
        val pin = pinField.text.toString()
        sp.edit().putString("pin", pin).apply()
        Thread {
            try {
                val c = URL("http://$h:$port$path").openConnection() as HttpURLConnection
                c.connectTimeout = 3000
                c.readTimeout = 4000
                c.setRequestProperty("X-Pin", pin)
                val code = c.responseCode
                val stream = if (code in 200..299) c.inputStream else c.errorStream
                val text = stream?.bufferedReader()?.readText() ?: ""
                val json = try { JSONObject(text) } catch (e: Exception) { JSONObject() }
                runOnUiThread {
                    if (code == 200) done(json, null)
                    else done(null, json.optString("error", "Error $code"))
                }
            } catch (e: Exception) {
                runOnUiThread { done(null, "Tidak bisa menghubungi TV") }
            }
        }.start()
    }

    private fun act(path: String) = call(path) { j, e -> if (j != null) render(j) else toast(e) }

    private fun refresh() = call("/status") { j, e ->
        if (j != null) {
            statusText.text = "Terhubung ke TV ($host)"
            render(j)
        } else {
            statusText.text = e ?: "Error"
            if (e == "Tidak bisa menghubungi TV") {
                // IP TV mungkin berubah: cari ulang.
                stopDiscovery()
                startDiscovery()
            }
        }
    }

    // ---------- Tampilan ----------

    private fun render(j: JSONObject) {
        lockText.text = if (j.optBoolean("lockAll")) "Status: SEMUA APLIKASI TERKUNCI" else "Status: Normal"

        val quiz = j.optJSONObject("quiz")
        val qOn = quiz?.optBoolean("enabled") ?: false
        val qInt = quiz?.optInt("interval", 10) ?: 10
        quizText.text = if (qOn) "Kuis: AKTIF, muncul setiap $qInt menit menonton" else "Kuis: MATI"
        quizToggle.text = if (qOn) "Matikan kuis" else "Nyalakan kuis"
        quizToggle.setOnClickListener { act("/setquiz?on=${if (qOn) 0 else 1}") }
        quizMinus.setOnClickListener { act("/setquiz?interval=${step(quizIntervals, qInt, -1)}") }
        quizPlus.setOnClickListener { act("/setquiz?interval=${step(quizIntervals, qInt, 1)}") }

        appsBox.removeAllViews()
        val arr = j.optJSONArray("apps") ?: return
        for (i in 0 until arr.length()) appsBox.addView(appRow(arr.getJSONObject(i)))
    }

    private fun appRow(a: JSONObject): LinearLayout {
        val pkg = a.getString("pkg")
        val q = URLEncoder.encode(pkg, "UTF-8")
        val limitOn = a.optBoolean("limitOn")
        val limit = a.optInt("limit", 60)
        val quiz = a.optBoolean("quiz")
        val remaining = a.optInt("remaining")

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(8))
        }
        box.addView(tv(a.getString("name"), 18f, true))

        val r1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        r1.addView(check("Batas waktu", limitOn) { c -> act("/setapp?pkg=$q&limitOn=${if (c) 1 else 0}") }, weighted())
        r1.addView(btn("-") { act("/setapp?pkg=$q&limit=${step(limits, limit, -1)}") })
        r1.addView(tv(" $limit mnt ", 16f))
        r1.addView(btn("+") { act("/setapp?pkg=$q&limit=${step(limits, limit, 1)}") })
        box.addView(r1)

        val r2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        r2.addView(check("Kuis", quiz) { c -> act("/setapp?pkg=$q&quiz=${if (c) 1 else 0}") }, weighted())
        if (limitOn) {
            r2.addView(tv(" sisa ${remaining / 60} mnt ", 16f))
            r2.addView(btn("+15") { act("/add?pkg=$q&min=15") })
        }
        box.addView(r2)
        return box
    }
}
