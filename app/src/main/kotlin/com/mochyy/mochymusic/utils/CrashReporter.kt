// Ported from Neiro (github.com/4nx3b/Neiro, com.music.bitchord.data.CrashReporter), app-private only:
// reports go to files/crash/crash-<yyyyMMdd-HHmmss>.log, kept across restarts, removed by clearing data.
// Read one with `adb shell run-as <package> ls files/crash`.

package com.mochyy.mochymusic.utils

import android.content.Context
import android.os.Build
import android.os.Process
import com.mochyy.mochymusic.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

object CrashReporter {
    private const val LOGCAT_LINES = 3000
    private const val LOGCAT_TIMEOUT_MS = 2_000L
    private const val LOGCAT_MAX_CHARS = 512 * 1024

    @Volatile
    private var installed = false

    fun crashDir(context: Context): File = File(context.filesDir, "crash")

    fun install(context: Context) {
        if (installed) return
        installed = true
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                CrashLog.writeReport(crashDir(app), thread, throwable, header(app)) { appendLogcat(it) }
                android.util.Log.e("MochyCrash", "crash report written to ${crashDir(app)}", throwable)
            } catch (_: Throwable) {
            } finally {
                if (previous != null) {
                    previous.uncaughtException(thread, throwable)
                } else {
                    Process.killProcess(Process.myPid())
                    exitProcess(10)
                }
            }
        }
    }

    private fun header(context: Context): List<String> = listOf(
        "App: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) ${BuildConfig.BUILD_TYPE}",
        "Package: ${context.packageName}",
        "Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})",
        "Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        "ABI: ${Build.SUPPORTED_ABIS.firstOrNull() ?: "?"}",
    )

    private fun appendLogcat(out: PrintWriter) {
        out.println()
        out.println("=== logcat (this process, last $LOGCAT_LINES lines) ===")
        val text = StringBuilder()
        val process = runCatching {
            ProcessBuilder("logcat", "-d", "-v", "threadtime", "-t", LOGCAT_LINES.toString(), "--pid=${Process.myPid()}")
                .redirectErrorStream(true).start()
        }.getOrElse {
            out.println("(logcat unavailable: $it)")
            return
        }
        val reader = Thread({
            runCatching {
                process.inputStream.bufferedReader().use { r ->
                    val buf = CharArray(8192)
                    while (true) {
                        val n = r.read(buf)
                        if (n < 0) break
                        synchronized(text) { if (text.length < LOGCAT_MAX_CHARS) text.append(buf, 0, n) }
                    }
                }
            }
        }, "crash-logcat").apply { isDaemon = true }
        reader.start()
        runCatching { process.waitFor(LOGCAT_TIMEOUT_MS, TimeUnit.MILLISECONDS) }
        runCatching { reader.join(500) }
        runCatching { process.destroy() }
        synchronized(text) { out.println(text) }
    }
}

/** feeds Timber into [CrashLog]'s ring buffer in every build; never throws */
class CrashLogTree : timber.log.Timber.Tree() {
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        try {
            val level = when (priority) {
                android.util.Log.VERBOSE, android.util.Log.DEBUG -> 'D'
                android.util.Log.INFO -> 'I'
                android.util.Log.WARN -> 'W'
                else -> 'E'
            }
            CrashLog.record(level, tag ?: "Mochy", message, t)
        } catch (_: Throwable) {
        }
    }
}
