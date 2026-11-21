package com.mochyy.mochymusic.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.nio.file.Files

class CrashExportTest {
    @Test
    fun pendingSkipsExportedAndOtherFiles() {
        val dir = Files.createTempDirectory("crash").toFile()
        File(dir, "crash-20260101-000000.log").writeText("a")
        File(dir, "crash-20260102-000000.log").writeText("b")
        File(dir, "crash-20260102-000000.log.exported").createNewFile()
        File(dir, "notes.txt").writeText("c")
        assertEquals(listOf("crash-20260101-000000.log"), CrashLog.pendingExports(dir).map { it.name })
        CrashLog.markExported(File(dir, "crash-20260101-000000.log"))
        assertEquals(emptyList<String>(), CrashLog.pendingExports(dir).map { it.name })
        assertEquals(2, dir.listFiles()!!.count { it.name.endsWith(".log") })
    }

    @Test
    fun toastText() {
        val one = "Crash log saved to %1${'$'}s"
        val many = "%1${'$'}d crash logs saved to %2${'$'}s"
        assertEquals("Crash log saved to Download/Mochy/crash-1.log", CrashLog.exportToast(listOf("crash-1.log"), "Download/Mochy", one, many))
        assertEquals("2 crash logs saved to Download/Mochy", CrashLog.exportToast(listOf("a", "b"), "Download/Mochy", one, many))
    }

    @Test
    fun pruneKeepsTenLogsAndDropsTheirMarkers() {
        val dir = Files.createTempDirectory("crash").toFile()
        for (i in 10..21) {
            File(dir, "crash-202601$i-000000.log").writeText("x")
            File(dir, "crash-202601$i-000000.log.exported").createNewFile()
        }
        CrashLog.writeReport(dir, Thread.currentThread(), RuntimeException("t"), keep = 10)
        assertEquals(10, dir.listFiles()!!.count { it.name.endsWith(".log") })
        val logs = dir.listFiles()!!.filter { it.name.endsWith(".log") }.map { it.name }.toSet()
        dir.listFiles()!!.filter { it.name.endsWith(".exported") }.forEach {
            assertEquals(true, it.name.removeSuffix(".exported") in logs)
        }
    }

    @Test
    fun missingDirIsEmpty() {
        assertEquals(emptyList<File>(), CrashLog.pendingExports(File("/nonexistent-crash-dir")))
    }
}
