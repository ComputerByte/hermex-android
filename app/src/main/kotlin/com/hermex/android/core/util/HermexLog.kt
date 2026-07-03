package com.hermex.android.core.util

import android.util.Log

/**
 * Thin, consistently-tagged wrapper around [android.util.Log] so `adb logcat` can filter on a
 * single prefix (`adb logcat "Hermex:*" "*:S"`) or a specific layer (`adb logcat -s Hermex/Sse`).
 * Deliberately logs event *types* and lengths, not raw chat content -- useful for diagnosing
 * timing/sequencing issues without dumping the user's conversation to the system log.
 */
object HermexLog {
    private const val PREFIX = "Hermex"

    fun d(tag: String, message: String) = Log.d("$PREFIX/$tag", message)
    fun w(tag: String, message: String, throwable: Throwable? = null) = Log.w("$PREFIX/$tag", message, throwable)
    fun e(tag: String, message: String, throwable: Throwable? = null) = Log.e("$PREFIX/$tag", message, throwable)
}
