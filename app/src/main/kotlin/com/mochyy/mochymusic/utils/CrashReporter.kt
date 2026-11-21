// Ported from Neiro (github.com/4nx3b/Neiro, com.music.bitchord.data.CrashReporter). At crash time the report is
// written to the private files/crash/crash-<yyyyMMdd-HHmmss>.log; on the next launch each one not yet exported is
// copied to the public Download/Mochy/ folder and a toast says where.

package com.mochyy.mochymusic.utils

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.mochyy.mochymusic.BuildConfig
import com.mochyy.mochymusic.R
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

    fun exportPending(context: Context) {
        runCatching {
            val app = context.applicationContext
            val dir = crashDir(app)
            val folder = Environment.DIRECTORY_DOWNLOADS + "/" + app.getString(R.string.app_name)
            val exported = mutableListOf<String>()
            var blocked = false
            for (log in CrashLog.pendingExports(dir)) {
                val ok = runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        copyToDownloads(app, log, folder)
                    } else if (ContextCompat.checkSelfPermission(app, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                        @Suppress("DEPRECATION")
                        val target = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), app.getString(R.string.app_name))
                        target.mkdirs()
                        log.copyTo(File(target, log.name), overwrite = true)
                        true
                    } else {
                        blocked = true
                        false
                    }
                }.getOrDefault(false)
                if (ok) {
                    CrashLog.markExported(log)
                    exported += log.name
                }
            }
            val message = when {
                exported.isNotEmpty() -> CrashLog.exportToast(
                    exported, folder,
                    app.getString(com.mochyy.mochymusic.R.string.crash_log_saved_one),
                    app.getString(com.mochyy.mochymusic.R.string.crash_log_saved_many),
                )
                blocked -> app.getString(com.mochyy.mochymusic.R.string.crash_log_saved_one, dir.absolutePath)
                else -> return
            }
            Handler(Looper.getMainLooper()).post {
                runCatching { Toast.makeText(app, message, Toast.LENGTH_LONG).show() }
            }
        }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private fun copyToDownloads(context: Context, log: File, folder: String): Boolean {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, log.name)
            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
            put(MediaStore.Downloads.RELATIVE_PATH, folder)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
        return runCatching {
            resolver.openOutputStream(uri)?.use { out -> log.inputStream().use { it.copyTo(out) } } ?: error("no stream")
            resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
            true
        }.getOrElse {
            resolver.delete(uri, null, null)
            false
        }
    }

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
