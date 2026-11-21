/*
 * Debug diagnostics, ported from Neiro (PR #35): StrictMode thread and VM
 * policies that log violations instead of crashing, and LeakCanary with heap
 * dumps turned off on emulators (where the heap dump kills the run more often
 * than it finds anything). Debug builds only — the release APK never sees any
 * of this.
 */

package com.mochyy.mochymusic.debug

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.StrictMode
import leakcanary.LeakCanary

class DebugDiagnosticsProvider : ContentProvider() {

    override fun onCreate(): Boolean {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .penaltyLog()
                .build(),
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedClosableObjects()
                .detectActivityLeaks()
                .detectLeakedRegistrationObjects()
                .penaltyLog()
                .build(),
        )
        if (runsOnEmulator()) {
            LeakCanary.config = LeakCanary.config.copy(dumpHeap = false)
        }
        return true
    }

    private fun runsOnEmulator(): Boolean =
        Build.HARDWARE.contains("ranchu") || Build.HARDWARE.contains("goldfish") ||
            Build.PRODUCT.startsWith("sdk") || Build.FINGERPRINT.contains("generic")

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0
}
