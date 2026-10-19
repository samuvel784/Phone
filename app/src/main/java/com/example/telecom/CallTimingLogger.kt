/*
 * Copyright (C) 2026 NovaDial contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.telecom

import android.os.SystemClock
import android.util.Log
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * Application-side timing instrumentation for the incoming-call path (Phase 7).
 *
 * It answers a single question honestly: **how much of the incoming-call delay is caused by
 * NovaDial itself?** Everything is measured with [SystemClock.uptimeMillis] (monotonic, unaffected
 * by wall-clock changes) relative to the earliest event NovaDial can observe — the platform
 * calling `InCallService.onCallAdded`.
 *
 * Time spent before that callback (radio paging, carrier signalling, Telecom connection setup and
 * the ringtone start request ordering) is invisible to the application and is therefore *not*
 * attributed to NovaDial.
 */
object CallTimingLogger {

    const val TAG = "NovaDialTiming"

    /** Event names used across the incoming-call path. */
    object Event {
        const val ON_CALL_ADDED = "onCallAdded"
        const val WAKELOCK_ACQUIRED = "wakeLockAcquired"
        const val RING_STARTED = "ringStarted"
        const val NOTIFICATION_POSTED = "notificationPosted"
        const val FULL_SCREEN_INTENT = "fullScreenIntentRequested"
        const val ACTIVITY_CREATED = "activityCreated"
        const val ACTIVITY_RESUMED = "activityResumed"
        const val FIRST_FRAME = "firstFrameRendered"
        const val CONTACT_RESOLVED = "contactNameResolved"
        const val CALL_SCREEN_LAUNCH = "callScreenLaunchRequested"
    }

    /** One measured mark, relative to the start of the trace. */
    data class Mark(val event: String, val offsetMs: Long)

    data class Report(
        val number: String,
        val isIncoming: Boolean,
        val startedAtUptimeMs: Long,
        val marks: List<Mark>
    ) {
        fun offsetOf(event: String): Long? = marks.firstOrNull { it.event == event }?.offsetMs

        /** Total app-side processing time from callback entry to the last recorded mark. */
        val totalMs: Long get() = marks.maxOfOrNull { it.offsetMs } ?: 0L

        fun format(): String = buildString {
            append("call=").append(number.ifBlank { "<unknown>" })
            append(" incoming=").append(isIncoming)
            marks.forEach { append(" | ").append(it.event).append("=").append(it.offsetMs).append("ms") }
        }
    }

    private const val MAX_REPORTS = 12
    private val reports = ConcurrentLinkedDeque<Report>()

    @Volatile
    private var current: Trace? = null

    /** Starts a trace for a call that just appeared. Safe to call from any thread. */
    fun begin(callId: Long, number: String, isIncoming: Boolean): Trace {
        val trace = Trace(callId, number, isIncoming, SystemClock.uptimeMillis())
        trace.mark(Event.ON_CALL_ADDED)
        current = trace
        return trace
    }

    /** The trace for the call that most recently arrived, if any. */
    fun activeTrace(): Trace? = current

    fun markActive(event: String) {
        current?.mark(event)
    }

    /** Clears the active trace once the call is fully presented. */
    fun finishActive() {
        current?.let { addReport(it.toReport()) }
        current = null
    }

    private fun addReport(report: Report) {
        reports.addFirst(report)
        while (reports.size > MAX_REPORTS) reports.pollLast()
    }

    /** The last few traces, newest first. Used by diagnostics and tests. */
    fun recentReports(): List<Report> = reports.toList()

    fun clear() {
        reports.clear()
        current = null
    }

    /**
     * A single incoming/outgoing call trace. Marking is cheap (a list append) so it can be used on
     * the critical incoming-call path without measurable cost.
     */
    class Trace internal constructor(
        val callId: Long,
        val number: String,
        val isIncoming: Boolean,
        private val startedAt: Long
    ) {
        private val marks = ArrayList<Mark>(12)

        @Synchronized
        fun mark(event: String) {
            val offset = SystemClock.uptimeMillis() - startedAt
            marks.add(Mark(event, offset))
            Log.i(TAG, "event=$event offset=${offset}ms call=$number incoming=$isIncoming")
        }

        @Synchronized
        fun toReport(): Report = Report(number, isIncoming, startedAt, marks.toList())

        fun elapsedMs(): Long = SystemClock.uptimeMillis() - startedAt
    }
}
