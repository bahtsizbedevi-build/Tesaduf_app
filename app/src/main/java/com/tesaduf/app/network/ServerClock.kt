package com.tesaduf.app.network

import java.time.OffsetDateTime

/**
 * Tracks the offset between the device clock and the server clock so countdowns use
 * server time. The authoritative expiry is always enforced by the backend; this only
 * keeps the visible timer honest when the phone's clock is wrong.
 */
class ServerClock(private val deviceNow: () -> Long = System::currentTimeMillis) {

    @Volatile
    var offsetMs: Long = 0L
        private set

    @Volatile
    private var hasSample = false

    fun now(): Long = deviceNow() + offsetMs

    /** [requestStartMs]/[requestEndMs] are device times around the request. */
    fun onServerTime(serverTimeIso: String?, requestStartMs: Long, requestEndMs: Long) {
        val serverMs = parseInstantMillis(serverTimeIso) ?: return
        val rtt = requestEndMs - requestStartMs
        if (rtt < 0 || rtt > MAX_USEFUL_RTT_MS) return
        val sample = serverMs - (requestStartMs + rtt / 2)
        // Smooth jitter once we have a baseline; jump immediately on large corrections.
        offsetMs = if (!hasSample || kotlin.math.abs(sample - offsetMs) > 5_000) sample
        else (offsetMs * 3 + sample) / 4
        hasSample = true
    }

    private companion object {
        const val MAX_USEFUL_RTT_MS = 10_000L
    }
}

/** Parses ISO-8601 timestamps from Postgres/Deno ("…+00:00" or "…Z"). */
fun parseInstantMillis(iso: String?): Long? {
    if (iso.isNullOrBlank()) return null
    return runCatching { OffsetDateTime.parse(iso).toInstant().toEpochMilli() }.getOrNull()
}
