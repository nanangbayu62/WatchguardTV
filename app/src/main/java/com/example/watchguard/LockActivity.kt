package com.example.watchguard

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast

class LockActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private val watcher = object : Runnable {
        override fun run() {
            if (shouldClose()) {
                finish()
                return
            }
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        render()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        render()
    }

    override fun onResume() {
        super.onResume()
        handler.post(watcher)
    }

    override fun onPause() {
        handler.removeCallbacks(watcher)
        super.onPause()
    }

    /** Tutup otomatis kalau orang tua membuka kunci / menambah waktu dari HP. */
    private fun shouldClose(): Boolean {
        val prefs = Prefs(this)
        val pkg = intent.getStringExtra("pkg") ?: ""
        return if (pkg == "*") !prefs.lockAll
        else !prefs.lockAll && prefs.remainingSeconds(pkg) > 0
    }

    private fun render() {
        val prefs = Prefs(this)
        val pkg = intent.getStringExtra("pkg") ?: ""
        val col = column().apply { gravity = Gravity.CENTER }
        val pin = pinField()

        if (pkg == "*") {
            col.addView(label("TV sedang dikunci oleh orang tua", 32f, true))
            col.addView(label("Aplikasi tidak bisa dibuka sampai orang tua membuka kunci.", 20f))
            col.addView(pin)
            col.addView(button("Buka kunci (PIN orang tua)") {
                if (prefs.pin.isNotEmpty() && pin.text.toString() == prefs.pin) {
                    prefs.lockAll = false
                    finish()
                } else {
                    Toast.makeText(this, "PIN salah", Toast.LENGTH_SHORT).show()
                    pin.text.clear()
                }
            })
        } else {
            val name = try {
                packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
            } catch (e: Exception) {
                pkg
            }
            col.addView(label("Waktu $name hari ini sudah habis", 32f, true))
            col.addView(label("Minta orang tua untuk menambah waktu.", 20f))
            col.addView(pin)
            col.addView(button("Tambah 15 menit untuk $name (PIN orang tua)") {
                if (prefs.pin.isNotEmpty() && pin.text.toString() == prefs.pin) {
                    prefs.addBonus(pkg, 15)
                    finish()
                } else {
                    Toast.makeText(this, "PIN salah", Toast.LENGTH_SHORT).show()
                    pin.text.clear()
                }
            })
        }
        col.addView(button("Kembali ke Home") { goHome() })
        setContentView(col)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        goHome()
    }

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finish()
    }
}
