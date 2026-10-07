package com.example.ch07.util

import java.util.concurrent.TimeUnit

fun formatRelativeTime(now: Long, then: Long): String {
    val diff = (now - then).coerceAtLeast(0)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
    val hours = TimeUnit.MILLISECONDS.toHours(diff)
    val days = TimeUnit.MILLISECONDS.toDays(diff)

    return when {
        diff < TimeUnit.MINUTES.toMillis(1) -> "baru saja"
        hours < 1 -> "$minutes menit lalu"
        days < 1 -> "$hours jam lalu"
        days < 30 -> "$days hari lalu"
        days < 365 -> "${days / 30} bulan lalu"
        else -> "${days / 365} tahun lalu"
    }
}