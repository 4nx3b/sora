/*
 * The Listen Together port's logging door, in the shape of Neiro's DebugLog
 * facade: every ported file logs through `Log` (the import alias), and this
 * shim hands each call to sora's own log tree (Timber).
 */

package com.mochyy.mochymusic.ported.together

import timber.log.Timber

object DebugLog {
    fun d(tag: String, message: String) {
        Timber.tag(tag).d(message)
    }

    fun d(tag: String, message: String, error: Throwable?) {
        if (error != null) Timber.tag(tag).d(error, message) else Timber.tag(tag).d(message)
    }

    fun i(tag: String, message: String) {
        Timber.tag(tag).i(message)
    }

    fun i(tag: String, message: String, error: Throwable?) {
        if (error != null) Timber.tag(tag).i(error, message) else Timber.tag(tag).i(message)
    }

    fun w(tag: String, message: String) {
        Timber.tag(tag).w(message)
    }

    fun w(tag: String, message: String, error: Throwable?) {
        if (error != null) Timber.tag(tag).w(error, message) else Timber.tag(tag).w(message)
    }

    fun e(tag: String, message: String) {
        Timber.tag(tag).e(message)
    }

    fun e(tag: String, message: String, error: Throwable?) {
        if (error != null) Timber.tag(tag).e(error, message) else Timber.tag(tag).e(message)
    }
}
