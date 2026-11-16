package com.example.watchguard

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var prefs: Prefs
    private var unlocked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        if (prefs.pin.isEmpty()) showSetPin() else showLogin()
    }

    override fun onResume() {
        super.onResume()
        if (unlocked) showSettings()
    }

    private fun show(col: LinearLayout) {
        setContentView(ScrollView(this).apply {
            setBackgroundColor(0xFF101418.toInt())
            addView(col)
        })
    }

    private fun showSetPin() {
        val col = column()
        col.addView(label("Buat PIN orang tua (4-6 digit)", 28f, true))
        val pin = pinField()
        col.addView(pin)
        col.addView(button("Simpan PIN") {
            val p = pin.text.toString()
            if (p.length in 4..6) {
                prefs.pin = p
                unlocked = true
                showSettings()
            } else {
                Toast.makeText(this, "PIN harus 4-6 digit", Toast.LENGTH_SHORT).show()
            }
        })
        show(col)
    }

    private fun showLogin() {
        val col = column()
        col.addView(label("Masukkan PIN orang tua", 28f, true))
        val pin = pinField()
        col.addView(pin)
        col.addView(button("Masuk") {
            if (pin.text.toString() == prefs.pin) {
                unlocked = true
                showSettings()
            } else {
                Toast.makeText(this, "PIN salah", Toast.LENGTH_SHORT).show()
                pin.text.clear()
            }
        })
        show(col)
    }

    private fun serviceEnabled(): Boolean {
        val s = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        return s.contains("$packageName/")
    }

    private fun showSettings() {
        val col = column()
        col.addView(label("WatchGuard - Pengaturan", 30f, true))
        col.addView(label(
            if (serviceEnabled()) "Layanan pemantau: AKTIF" else "Layanan pemantau: MATI (aktifkan lewat adb)", 20f))
        col.addView(button("Buka Pengaturan Aksesibilitas") {
            try {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(this, "TV ini tidak punya menu Aksesibilitas, aktifkan lewat adb", Toast.LENGTH_LONG).show()
            }
        })
        col.addView(button("Reset waktu hari ini (semua aplikasi)") {
            prefs.resetToday()
            showSettings()
        })

        col.addView(label("Kuis matematika (SD) untuk lanjut menonton", 22f, true))
        col.addView(label(
            if (prefs.quizEnabled) "Kuis: AKTIF, muncul setiap ${prefs.quizInterval()} menit menonton"
            else "Kuis: MATI", 18f))
        val quizRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        quizRow.addView(button(if (prefs.quizEnabled) "Matikan kuis" else "Nyalakan kuis") {
            prefs.quizEnabled = !prefs.quizEnabled
            prefs.resetAllQuiz()
            showSettings()
        })
        quizRow.addView(button("- jeda") { prefs.stepQuizInterval(-1); showSettings() })
        quizRow.addView(button("+ jeda") { prefs.stepQuizInterval(1); showSettings() })
        col.addView(quizRow)
        col.addView(label("Kuis hanya muncul di aplikasi yang dicentang \"Kuis\" di daftar bawah.", 16f))

        col.addView(label("Centang nama aplikasi untuk batas waktu (terpisah tiap aplikasi). Centang Kuis untuk kuis matematika di aplikasi itu.", 18f))

        val pm = packageManager
        val intents = listOf(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER),
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        )
        val apps = intents
            .flatMap { pm.queryIntentActivities(it, 0) }
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .distinctBy { it.first }
            .filter { it.first != packageName }
            .sortedBy { it.second.lowercase() }

        for ((pkg, name) in apps) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val info = label("", 18f)
            fun refreshRow() {
                info.text = " ${prefs.limitMinutes(pkg)} mnt/hari (sisa ${maxOf(0, prefs.remainingSeconds(pkg)) / 60}) "
            }
            refreshRow()

            val cb = CheckBox(this).apply {
                text = name
                textSize = 20f
                setTextColor(0xFFFFFFFF.toInt())
                highlightOnFocus()
                isChecked = pkg in prefs.blocked
                setOnCheckedChangeListener { _, checked ->
                    prefs.blocked = if (checked) prefs.blocked + pkg else prefs.blocked - pkg
                }
            }
            row.addView(cb, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(info)
            row.addView(button("-") { prefs.stepLimit(pkg, -1); refreshRow() })
            row.addView(button("+") { prefs.stepLimit(pkg, 1); refreshRow() })
            row.addView(CheckBox(this).apply {
                text = "Kuis"
                textSize = 18f
                setTextColor(0xFFFFFFFF.toInt())
                highlightOnFocus()
                isChecked = pkg in prefs.quizApps
                setOnCheckedChangeListener { _, checked ->
                    prefs.quizApps = if (checked) prefs.quizApps + pkg else prefs.quizApps - pkg
                }
            })
            col.addView(row)
        }

        col.addView(button("Ganti PIN") { unlocked = false; showSetPin() })
        show(col)
    }
}
