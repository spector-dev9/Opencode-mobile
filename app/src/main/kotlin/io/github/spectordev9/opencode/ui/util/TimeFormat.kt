package io.github.spectordev9.opencode.ui.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToLong

fun formatRelativeTime(
    epochMillis: Long,
    now: Long = System.currentTimeMillis(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): String {
    val delta = (now - epochMillis).coerceAtLeast(0)
    val minutes = delta / 60_000
    val hours = delta / 3_600_000
    val days = delta / 86_400_000
    return when {
        delta < 60_000 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        days < 7 -> "${days}d ago"
        else -> {
            val instant = Instant.ofEpochMilli(epochMillis).atZone(zoneId)
            val current = Instant.ofEpochMilli(now).atZone(zoneId)
            val pattern = if (instant.year == current.year) "MMM d" else "MMM d, yyyy"
            instant.format(DateTimeFormatter.ofPattern(pattern, Locale.US))
        }
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes < 1_024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unitIndex = -1
    while (value >= 1_024 && unitIndex < units.lastIndex) {
        value /= 1_024
        unitIndex += 1
    }
    val rounded = (value * 10).roundToLong() / 10.0
    return if (rounded >= 100) {
        "${rounded.roundToLong()} ${units[unitIndex]}"
    } else {
        "$rounded ${units[unitIndex]}"
    }
}

fun formatTokenCount(tokens: Long): String = when {
    tokens < 1_000 -> tokens.toString()
    tokens < 1_000_000 -> {
        val value = (tokens / 100.0).roundToLong() / 10.0
        "${value}k"
    }
    else -> {
        val value = (tokens / 100_000.0).roundToLong() / 10.0
        "${value}M"
    }
}

fun formatCost(cost: Double): String {
    if (cost <= 0.0) return "$0.00"
    return if (cost < 0.01) {
        "<$0.01"
    } else {
        "$" + String.format(Locale.US, "%.2f", cost)
    }
}
