package com.example.watchguard

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.Toast
import kotlin.random.Random

/** Kuis matematika tingkat SD yang harus dijawab untuk melanjutkan menonton aplikasi tertentu. */
class QuizActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private var pkg = ""
    private var question = ""
    private var answer = 0
    private var options: List<Int> = emptyList()

    private val watcher = object : Runnable {
        override fun run() {
            // Tutup otomatis kalau kuis dimatikan atau dilewati dari tempat lain.
            if (!Prefs(this@QuizActivity).quizDue(pkg)) {
                finish()
                return
            }
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        pkg = intent.getStringExtra("pkg") ?: ""
        newQuestion()
        render()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent) // soal yang sedang tampil tidak diganti
    }

    override fun onResume() {
        super.onResume()
        handler.post(watcher)
    }

    override fun onPause() {
        handler.removeCallbacks(watcher)
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun appName(): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) {
        pkg
    }

    private fun newQuestion() {
        when (Random.nextInt(4)) {
            0 -> {
                val a = Random.nextInt(1, 51)
                val b = Random.nextInt(1, 51)
                question = "$a + $b = ?"
                answer = a + b
            }
            1 -> {
                val a = Random.nextInt(10, 61)
                val b = Random.nextInt(1, a + 1)
                question = "$a - $b = ?"
                answer = a - b
            }
            2 -> {
                val a = Random.nextInt(2, 11)
                val b = Random.nextInt(2, 11)
                question = "$a × $b = ?"
                answer = a * b
            }
            else -> {
                val b = Random.nextInt(2, 11)
                val q = Random.nextInt(2, 11)
                question = "${b * q} ÷ $b = ?"
                answer = q
            }
        }
        val set = linkedSetOf(answer)
        while (set.size < 4) {
            val d = answer + Random.nextInt(-10, 11)
            if (d >= 0) set.add(d)
        }
        options = set.toList().shuffled()
    }

    private fun render() {
        val prefs = Prefs(this)
        val col = column().apply { gravity = Gravity.CENTER }
        col.addView(label("Kuis Matematika", 32f, true))
        col.addView(label("Jawab dengan benar untuk lanjut menonton ${appName()}.", 20f))
        col.addView(label(question, 56f, true))

        val buttons = ArrayList<Button>()
        for (opt in options) {
            val b = button("$opt") { onPick(opt, buttons) }
            buttons.add(b)
            col.addView(b)
        }

        col.addView(label("Atau minta orang tua melewati kuis:", 16f))
        val pin = pinField()
        col.addView(pin)
        col.addView(button("Lewati (PIN orang tua)") {
            if (prefs.pin.isNotEmpty() && pin.text.toString() == prefs.pin) {
                prefs.resetQuiz(pkg)
                finish()
            } else {
                Toast.makeText(this, "PIN salah", Toast.LENGTH_SHORT).show()
                pin.text.clear()
            }
        })
        col.addView(button("Kembali ke Home") { goHome() })
        setContentView(col)
    }

    private fun onPick(opt: Int, buttons: List<Button>) {
        if (opt == answer) {
            Prefs(this).resetQuiz(pkg)
            finish()
        } else {
            Toast.makeText(this, "Salah. Tunggu sebentar, soal baru akan muncul.", Toast.LENGTH_SHORT).show()
            buttons.forEach { it.isEnabled = false }
            handler.postDelayed({
                newQuestion()
                render()
            }, 3000)
        }
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
