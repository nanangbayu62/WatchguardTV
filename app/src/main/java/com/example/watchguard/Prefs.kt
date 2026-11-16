package com.example.watchguard

import android.content.Context
import java.time.LocalDate

class Prefs(ctx: Context) {
    companion object {
        // Pilihan batas (menit). 1 menit untuk uji coba.
        val OPTIONS = listOf(1, 5, 15, 30, 45, 60, 90, 120, 180, 240)
        // Pilihan jeda kuis (menit menonton). 1 menit untuk uji coba.
        val QUIZ_OPTIONS = listOf(1, 5, 10, 15, 20, 30)
    }

    private val sp = ctx.applicationContext.getSharedPreferences("watchguard", Context.MODE_PRIVATE)

    var pin: String
        get() = sp.getString("pin", "") ?: ""
        set(v) { sp.edit().putString("pin", v).apply() }

    var blocked: Set<String>
        get() = HashSet(sp.getStringSet("blocked", emptySet()) ?: emptySet())
        set(v) { sp.edit().putStringSet("blocked", HashSet(v)).apply() }

    var lockAll: Boolean
        get() = sp.getBoolean("lockall", false)
        set(v) { sp.edit().putBoolean("lockall", v).apply() }

    var quizEnabled: Boolean
        get() = sp.getBoolean("quiz_on", false)
        set(v) { sp.edit().putBoolean("quiz_on", v).apply() }

    fun quizInterval(): Int = sp.getInt("quiz_int", 10)

    fun setQuizInterval(m: Int) { sp.edit().putInt("quiz_int", m.coerceIn(1, 60)).apply() }

    fun stepQuizInterval(dir: Int): Int {
        val cur = quizInterval()
        val idx = QUIZ_OPTIONS.indexOfFirst { it >= cur }.let { if (it < 0) QUIZ_OPTIONS.lastIndex else it }
        val next = QUIZ_OPTIONS[(idx + dir).coerceIn(0, QUIZ_OPTIONS.lastIndex)]
        sp.edit().putInt("quiz_int", next).apply()
        return next
    }

    /** Aplikasi yang memakai kuis (dipilih terpisah dari aplikasi berbatas waktu). */
    var quizApps: Set<String>
        get() = HashSet(sp.getStringSet("quiz_apps", emptySet()) ?: emptySet())
        set(v) { sp.edit().putStringSet("quiz_apps", HashSet(v)).apply() }

    fun addQuizSeconds(pkg: String, s: Int) {
        sp.edit().putInt("quiz_sec_$pkg", sp.getInt("quiz_sec_$pkg", 0) + s).apply()
    }

    fun resetQuiz(pkg: String) { sp.edit().putInt("quiz_sec_$pkg", 0).apply() }

    fun resetAllQuiz() {
        val e = sp.edit()
        for (k in sp.all.keys) if (k.startsWith("quiz_sec_")) e.remove(k)
        e.apply()
    }

    fun quizDue(pkg: String): Boolean =
        quizEnabled && pkg in quizApps && sp.getInt("quiz_sec_$pkg", 0) >= quizInterval() * 60

    fun limitMinutes(pkg: String): Int = sp.getInt("lim_$pkg", 60)

    fun setLimit(pkg: String, m: Int) { sp.edit().putInt("lim_$pkg", m.coerceIn(1, 720)).apply() }

    /** Geser batas ke pilihan berikutnya (dir = +1) atau sebelumnya (dir = -1). */
    fun stepLimit(pkg: String, dir: Int): Int {
        val cur = limitMinutes(pkg)
        val idx = OPTIONS.indexOfFirst { it >= cur }.let { if (it < 0) OPTIONS.lastIndex else it }
        val next = OPTIONS[(idx + dir).coerceIn(0, OPTIONS.lastIndex)]
        sp.edit().putInt("lim_$pkg", next).apply()
        return next
    }

    private fun clearUsage(e: android.content.SharedPreferences.Editor) {
        for (k in sp.all.keys) {
            if (k.startsWith("used_") || k.startsWith("bonus_")) e.remove(k)
        }
    }

    private fun rollover() {
        val today = LocalDate.now().toString()
        if (sp.getString("day", "") != today) {
            val e = sp.edit()
            clearUsage(e)
            e.putString("day", today).apply()
        }
    }

    fun addUsed(pkg: String, seconds: Int) {
        rollover()
        sp.edit().putInt("used_$pkg", sp.getInt("used_$pkg", 0) + seconds).apply()
    }

    fun addBonus(pkg: String, minutes: Int) {
        rollover()
        sp.edit().putInt("bonus_$pkg", sp.getInt("bonus_$pkg", 0) + minutes).apply()
    }

    fun resetToday() {
        val e = sp.edit()
        clearUsage(e)
        e.putString("day", LocalDate.now().toString()).apply()
    }

    fun remainingSeconds(pkg: String): Int {
        rollover()
        return (limitMinutes(pkg) + sp.getInt("bonus_$pkg", 0)) * 60 - sp.getInt("used_$pkg", 0)
    }
}
