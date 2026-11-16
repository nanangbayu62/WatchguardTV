package com.example.watchguard

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.accessibility.AccessibilityEvent

class WatchGuardService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var prefs: Prefs
    private var currentPkg = ""
    private var exempt: Set<String> = emptySet()
    private var ticks = 0
    private var server: ControlServer? = null
    private var nsdListener: NsdManager.RegistrationListener? = null

    private val tick = object : Runnable {
        override fun run() {
            tickOnce()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = Prefs(this)
        refreshExempt()
        startServer()
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    /** Aplikasi yang tidak pernah dikunci: launcher/home, system UI, dan WatchGuard sendiri. */
    private fun refreshExempt() {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val set = HashSet<String>()
        for (r in packageManager.queryIntentActivities(home, 0)) set.add(r.activityInfo.packageName)
        set.add("com.android.systemui")
        set.add("android")
        set.add(packageName)
        exempt = set
    }

    private fun startServer() {
        server?.stop()
        val s = ControlServer(this, prefs)
        if (s.start()) {
            server = s
            registerNsd(s.port)
        }
    }

    private fun registerNsd(port: Int) {
        try {
            val info = NsdServiceInfo()
            info.serviceName = "WatchGuard-TV"
            info.serviceType = "_watchguard._tcp."
            info.setPort(port)
            val l = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(i: NsdServiceInfo) {}
                override fun onRegistrationFailed(i: NsdServiceInfo, e: Int) {}
                override fun onServiceUnregistered(i: NsdServiceInfo) {}
                override fun onUnregistrationFailed(i: NsdServiceInfo, e: Int) {}
            }
            nsdListener = l
            (getSystemService(NSD_SERVICE) as NsdManager)
                .registerService(info, NsdManager.PROTOCOL_DNS_SD, l)
        } catch (_: Exception) {
        }
    }

    private fun unregisterNsd() {
        val l = nsdListener ?: return
        try {
            (getSystemService(NSD_SERVICE) as NsdManager).unregisterService(l)
        } catch (_: Exception) {
        }
        nsdListener = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Pantauan utama ada di tickOnce(); event ini hanya mempercepat reaksi.
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == "com.android.systemui") return
        currentPkg = pkg
        if (prefs.lockAll) {
            if (pkg !in exempt) showLock("*")
        } else if (pkg in prefs.blocked && prefs.remainingSeconds(pkg) <= 0) {
            showLock(pkg)
        } else if (prefs.quizDue(pkg)) {
            showQuiz(pkg)
        }
    }

    private fun tickOnce() {
        ticks++
        if (ticks % 300 == 0) {
            // Segarkan daftar launcher dan daftarkan ulang NSD (jaga-jaga jaringan berubah).
            refreshExempt()
            server?.let { unregisterNsd(); registerNsd(it.port) }
        }

        val active = rootInActiveWindow?.packageName?.toString()
        if (active != null && active != "com.android.systemui") currentPkg = active

        val pkg = currentPkg
        if (pkg.isEmpty()) return

        if (prefs.lockAll) {
            if (pkg !in exempt) showLock("*")
            return
        }

        val inLimit = pkg in prefs.blocked
        val inQuiz = prefs.quizEnabled && pkg in prefs.quizApps
        if (!inLimit && !inQuiz) return
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (!pm.isInteractive) return

        if (inLimit) {
            prefs.addUsed(pkg, 1)
            if (prefs.remainingSeconds(pkg) <= 0) {
                showLock(pkg)
                return
            }
        }
        if (inQuiz) {
            prefs.addQuizSeconds(pkg, 1)
            if (prefs.quizDue(pkg)) showQuiz(pkg)
        }
    }

    private fun showQuiz(pkg: String) {
        startActivity(
            Intent(this, QuizActivity::class.java)
                .putExtra("pkg", pkg)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
    }

    private fun showLock(pkg: String) {
        startActivity(
            Intent(this, LockActivity::class.java)
                .putExtra("pkg", pkg)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        unregisterNsd()
        server?.stop()
        server = null
        super.onDestroy()
    }
}
