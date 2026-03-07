package com.clockweather.app.util

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

object DateFormatter {

    fun formatTime(time: LocalTime, is24Hour: Boolean): String {
        val pattern = if (is24Hour) "HH:mm" else "h:mm a"
        return time.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
    }

    fun formatDateTime(dateTime: LocalDateTime, is24Hour: Boolean): String {
        val timePattern = if (is24Hour) "HH:mm" else "h:mm a"
        val pattern = "EEE, MMM d · $timePattern"
        return dateTime.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
    }

    fun formatDate(date: LocalDate): String {
        return date.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault()))
    }

    fun formatDayName(date: LocalDate): String {
        return date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
    }

    fun formatDayShort(date: LocalDate): String {
        return date.format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))
    }

    fun formatDuration(seconds: Double): String {
        val totalMinutes = (seconds / 60).toInt()
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return "${hours}h ${minutes}m"
    }

    fun minutesAgo(dateTime: LocalDateTime): Int {
        return ChronoUnit.MINUTES.between(dateTime, LocalDateTime.now()).toInt().coerceAtLeast(0)
    }

    fun formatLastUpdated(dateTime: LocalDateTime): String {
        val minutes = minutesAgo(dateTime)
        return when {
            minutes < 1 -> "just now"
            minutes == 1 -> "1 minute ago"
            minutes < 60 -> "$minutes minutes ago"
            minutes < 120 -> "1 hour ago"
            else -> "${minutes / 60} hours ago"
        }
    }
}

