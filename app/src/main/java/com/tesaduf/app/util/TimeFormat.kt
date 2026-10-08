package com.tesaduf.app.util

import com.tesaduf.app.network.parseInstantMillis
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** "mm:ss" for countdowns; never negative, rounds up so 0.4 s left still shows 00:01. */
fun formatCountdown(remainingMs: Long): String {
    val totalSeconds = (remainingMs.coerceAtLeast(0) + 999) / 1000
    return String.format(Locale.ROOT, "%02d:%02d", totalSeconds / 60, totalSeconds % 60)
}

private val TurkishLocale: Locale = Locale.forLanguageTag("tr-TR")
private val DayTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm", TurkishLocale)
private val ClockFormatter = DateTimeFormatter.ofPattern("HH:mm", TurkishLocale)

fun formatDayTime(iso: String?): String = format(iso, DayTimeFormatter)

fun formatClock(iso: String?): String = format(iso, ClockFormatter)

private fun format(iso: String?, formatter: DateTimeFormatter): String {
    val ms = parseInstantMillis(iso) ?: return ""
    return formatter.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))
}
