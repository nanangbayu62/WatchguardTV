package com.example.watchguard

import android.content.Context
import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import kotlin.concurrent.thread

/** Server HTTP mini untuk kontrol dari HP. Hanya menerima koneksi dari jaringan lokal dan wajib PIN. */
class ControlServer(private val ctx: Context, private val prefs: Prefs) {

    private var server: ServerSocket? = null
    @Volatile private var running = false
    private var fails = 0
    private var blockedUntil = 0L

    val port: Int get() = server?.localPort ?: -1

    fun start(): Boolean {
        if (running) return true
        server = try {
            ServerSocket(8765)
        } catch (e: Exception) {
            try { ServerSocket(0) } catch (e2: Exception) { return false }
        }
        running = true
        thread(isDaemon = true) { acceptLoop() }
        return true
    }

    fun stop() {
        running = false
        try { server?.close() } catch (_: Exception) {}
        server = null
    }

    private fun acceptLoop() {
        val s = server ?: return
        while (running) {
            try {
                val c = s.accept()
                thread(isDaemon = true) { handle(c) }
            } catch (e: Exception) {
                if (!running) break
            }
        }
    }

    private fun handle(c: Socket) {
        c.use {
            try {
                c.soTimeout = 5000
                val addr = c.inetAddress
                if (!(addr.isSiteLocalAddress || addr.isLoopbackAddress || addr.isLinkLocalAddress)) {
                    respond(c, 403, JSONObject().put("error", "forbidden"))
                    return
                }
                val reader = BufferedReader(InputStreamReader(c.getInputStream()))
                val first = reader.readLine() ?: return
                var pin = ""
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) break
                    if (line.startsWith("X-Pin:", ignoreCase = true)) pin = line.substring(6).trim()
                }
                val parts = first.split(" ")
                if (parts.size < 2) return
                val target = parts[1]
                val path = target.substringBefore("?")
                val query = parse(target.substringAfter("?", ""))

                if (path == "/ping") {
                    respond(c, 200, JSONObject().put("app", "watchguard"))
                    return
                }

                val now = System.currentTimeMillis()
                synchronized(this) {
                    if (now < blockedUntil) {
                        respond(c, 429, JSONObject().put("error", "Terlalu banyak PIN salah, coba lagi 1 menit"))
                        return
                    }
                    if (prefs.pin.isEmpty() || pin != prefs.pin) {
                        fails++
                        if (fails >= 5) { blockedUntil = now + 60_000; fails = 0 }
                        respond(c, 401, JSONObject().put("error", "PIN salah"))
                        return
                    }
                    fails = 0
                }

                when (path) {
                    "/status" -> {}
                    "/lock" -> prefs.lockAll = true
                    "/unlock" -> prefs.lockAll = false
                    "/reset" -> prefs.resetToday()
                    "/add" -> {
                        val p = query["pkg"] ?: ""
                        val m = (query["min"]?.toIntOrNull() ?: 15).coerceIn(1, 240)
                        if (p in prefs.blocked) prefs.addBonus(p, m)
                    }
                    "/skipquiz" -> {
                        val p = query["pkg"] ?: ""
                        if (p.isNotEmpty()) prefs.resetQuiz(p)
                    }
                    "/setquiz" -> {
                        query["on"]?.let {
                            prefs.quizEnabled = it == "1"
                            prefs.resetAllQuiz()
                        }
                        query["interval"]?.toIntOrNull()?.let { prefs.setQuizInterval(it) }
                    }
                    "/setapp" -> {
                        val p = query["pkg"] ?: ""
                        if (p.isNotEmpty() && p != ctx.packageName) {
                            query["limitOn"]?.let {
                                prefs.blocked = if (it == "1") prefs.blocked + p else prefs.blocked - p
                            }
                            query["limit"]?.toIntOrNull()?.let { prefs.setLimit(p, it) }
                            query["quiz"]?.let {
                                prefs.quizApps = if (it == "1") prefs.quizApps + p else prefs.quizApps - p
                            }
                        }
                    }
                    else -> {
                        respond(c, 404, JSONObject().put("error", "not found"))
                        return
                    }
                }
                respond(c, 200, status())
            } catch (_: Exception) {
            }
        }
    }

    private fun launcherApps(): List<Pair<String, String>> {
        val pm = ctx.packageManager
        val intents = listOf(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER),
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        )
        return intents
            .flatMap { pm.queryIntentActivities(it, 0) }
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .distinctBy { it.first }
            .filter { it.first != ctx.packageName }
            .sortedBy { it.second.lowercase() }
    }

    /** Semua pengaturan TV dalam satu JSON: kunci, kuis, dan seluruh aplikasi di TV. */
    private fun status(): JSONObject {
        val blocked = prefs.blocked
        val quizApps = prefs.quizApps
        val arr = JSONArray()
        for ((p, name) in launcherApps()) {
            arr.put(
                JSONObject()
                    .put("pkg", p)
                    .put("name", name)
                    .put("limitOn", p in blocked)
                    .put("limit", prefs.limitMinutes(p))
                    .put("remaining", maxOf(0, prefs.remainingSeconds(p)))
                    .put("quiz", p in quizApps)
            )
        }
        return JSONObject()
            .put("lockAll", prefs.lockAll)
            .put("quiz", JSONObject().put("enabled", prefs.quizEnabled).put("interval", prefs.quizInterval()))
            .put("apps", arr)
    }

    private fun parse(q: String): Map<String, String> =
        q.split("&").filter { it.contains("=") }.associate {
            val kv = it.split("=", limit = 2)
            URLDecoder.decode(kv[0], "UTF-8") to URLDecoder.decode(kv[1], "UTF-8")
        }

    private fun respond(c: Socket, code: Int, json: JSONObject) {
        val body = json.toString().toByteArray(Charsets.UTF_8)
        val status = when (code) {
            200 -> "OK"; 401 -> "Unauthorized"; 403 -> "Forbidden"
            404 -> "Not Found"; 429 -> "Too Many Requests"; else -> "Error"
        }
        val head = "HTTP/1.1 $code $status\r\n" +
            "Content-Type: application/json; charset=utf-8\r\n" +
            "Content-Length: ${body.size}\r\nConnection: close\r\n\r\n"
        val out = c.getOutputStream()
        out.write(head.toByteArray(Charsets.UTF_8))
        out.write(body)
        out.flush()
    }
}
