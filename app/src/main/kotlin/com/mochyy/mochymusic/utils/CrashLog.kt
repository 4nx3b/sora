// Ported from Neiro (github.com/4nx3b/Neiro, com.music.bitchord.data.CrashLog), itself from ArchiveTune.
// A ring buffer of the app's recent log lines plus the report writer. No android.* so it runs in JVM tests.

package com.mochyy.mochymusic.utils

import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashLog {
    internal const val CAPACITY = 4000

    private val buffer: Array<String?> = arrayOfNulls(CAPACITY)
    private var index = 0
    private var count = 0

    @Synchronized
    fun record(level: Char, tag: String, message: String, error: Throwable?) {
        val line = buildString {
            append(timestamp()).append(' ').append(level).append(' ').append(tag).append(": ").append(message)
            if (error != null) append('\n').append(error.stackTraceString())
        }
        buffer[index] = line
        index = (index + 1) % CAPACITY
        if (count < CAPACITY) count++
    }

    @Synchronized
    fun snapshot(): List<String> {
        val n = count
        if (n == 0) return emptyList()
        val start = if (n >= CAPACITY) index else 0
        return (0 until n).mapNotNull { buffer[(start + it) % CAPACITY] }
    }

    @Synchronized
    internal fun clear() {
        buffer.fill(null)
        index = 0
        count = 0
    }

    fun fileName(date: Date = Date()): String =
        "crash-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(date) + ".log"

    /** writes the report into [dir] synchronously, keeps the newest [keep] reports, and returns the file */
    fun writeReport(
        dir: File,
        thread: Thread?,
        throwable: Throwable,
        header: List<String> = emptyList(),
        keep: Int = 10,
        appendix: (PrintWriter) -> Unit = {},
    ): File {
        dir.mkdirs()
        val file = File(dir, fileName())
        val lines = snapshot()
        file.printWriter().use { out ->
            out.println("=== Mochy crash report ===")
            out.println("Time: ${timestamp()}")
            out.println("Thread: ${thread?.name ?: "?"}")
            header.forEach(out::println)
            out.println()
            out.println("=== Uncaught exception ===")
            throwable.printStackTrace(out)
            out.println()
            out.println("=== Recent app log (last ${lines.size} lines, oldest first) ===")
            lines.forEach(out::println)
            out.flush()
            runCatching { appendix(out) }
        }
        runCatching {
            dir.listFiles { f -> f.isFile && f.name.startsWith("crash-") && f.name.endsWith(".log") }
                ?.sortedByDescending { it.name }
                ?.drop(keep)
                ?.forEach {
                    it.delete()
                    File(dir, it.name + EXPORTED_SUFFIX).delete()
                }
        }
        return file
    }

    fun pendingExports(dir: File): List<File> =
        dir.listFiles { f -> f.isFile && f.name.startsWith("crash-") && f.name.endsWith(".log") }
            .orEmpty()
            .filter { !File(dir, it.name + EXPORTED_SUFFIX).exists() }
            .sortedBy { it.name }

    fun markExported(log: File) {
        File(log.parentFile, log.name + EXPORTED_SUFFIX).createNewFile()
    }

    fun exportToast(names: List<String>, folder: String, one: String, many: String): String =
        if (names.size == 1) one.format("$folder/${names[0]}") else many.format(names.size, folder)

    const val EXPORTED_SUFFIX = ".exported"

    internal fun timestamp(): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())

    private fun Throwable.stackTraceString(): String =
        StringWriter().also { PrintWriter(it).use { pw -> printStackTrace(pw) } }.toString()
}
