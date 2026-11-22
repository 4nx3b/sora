// ExternalDownloader.kt
// Neiro's "External downloader": with it on and an app named, the download button hands the song's
// YouTube address to that app instead of saving it here. The plain ACTION_VIEW is tried first,
// then a share, then the app's own launch intent — whatever the downloader accepts.

package com.mochyy.mochymusic.playback

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri

/** whether the hand-off found an app that took it */
fun handToExternalDownloader(context: Context, packageName: String, url: String): Boolean {
    val view = Intent(Intent.ACTION_VIEW, url.toUri()).setPackage(packageName)
    if (view.resolveActivity(context.packageManager) != null) {
        context.startActivity(view.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }
    val share = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, url)
        .setPackage(packageName)
    if (share.resolveActivity(context.packageManager) != null) {
        context.startActivity(
            Intent.createChooser(share, null)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return true
    }
    context.packageManager.getLaunchIntentForPackage(packageName)?.let { launch ->
        context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }
    return false
}

/** the watch address of a song id, the page every YouTube downloader understands */
fun externalDownloadUrl(songId: String): String = "https://music.youtube.com/watch?v=$songId"
