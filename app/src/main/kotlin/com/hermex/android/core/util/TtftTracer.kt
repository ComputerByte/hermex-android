package com.hermex.android.core.util

import android.os.SystemClock
import com.hermex.android.BuildConfig
import java.util.concurrent.ConcurrentHashMap

/**
 * Debug-only stage-timing trace for one chat turn, from the Send tap through the first token
 * hitting the screen. Every [mark] logs the elapsed time since the matching [start] under the
 * `Hermex/Ttft` tag so a run can be reconstructed from `adb logcat -s Hermex/Ttft` alone.
 *
 * No-ops entirely in release builds ([BuildConfig.DEBUG] false): this exists to answer a TTFT
 * investigation, not to ship as always-on telemetry.
 *
 * Lifecycle: [start] is called exactly once per turn, from [com.hermex.android.chat.ChatViewModel.sendMessage]
 * itself (not from the UI layer) -- `regenerate`/`retryLastMessage` both funnel through that same
 * function, so every send-shaped entry point re-arms the trace there and none of them can log
 * marks against a stale timer left over from a previous turn. [finish] is called from
 * `finalizeStream` (the single terminal point for done/stream_end/cancel/error alike) and from
 * `reattachStream` before it starts observing a stream that was never `start()`-ed by this
 * process -- both disarm the tracer so a late/unrelated event afterward (a stray heartbeat, a
 * reattach with no "send tapped" moment of its own) is silently dropped instead of being
 * misattributed to whatever turn happened to run last. True concurrent turns can't reach this
 * object at all: `sendMessage` itself refuses to proceed while `isSending`/`isStreaming` is
 * already true, so [start] can never be called twice for two genuinely overlapping turns.
 */
object TtftTracer {
    private val ENABLED = BuildConfig.DEBUG

    @Volatile private var startNanos: Long = 0L
    @Volatile private var armed = false
    private val firedStages = ConcurrentHashMap.newKeySet<String>()

    fun start() {
        if (!ENABLED) return
        startNanos = SystemClock.elapsedRealtimeNanos()
        firedStages.clear()
        armed = true
        HermexLog.d("Ttft", "==== turn start ====")
        mark("Send tapped")
    }

    /** Ends the current trace, if any -- see class doc for when this is called. Idempotent. */
    fun finish() {
        if (!ENABLED || !armed) return
        armed = false
        HermexLog.d("Ttft", "==== turn end ====")
    }

    fun mark(stage: String) {
        if (!ENABLED || !armed) return
        val elapsedMs = (SystemClock.elapsedRealtimeNanos() - startNanos) / 1_000_000
        HermexLog.d("Ttft", "$stage +${elapsedMs}ms")
    }

    /** Like [mark], but only logs the first time [stage] fires since the last [start] -- for
     * callbacks that repeat every token/line, where only the first occurrence belongs on a TTFT
     * trace (e.g. "first SSE event", "first token parsed"). */
    fun markOnce(stage: String) {
        if (!ENABLED || !armed) return
        if (firedStages.add(stage)) mark(stage)
    }
}
