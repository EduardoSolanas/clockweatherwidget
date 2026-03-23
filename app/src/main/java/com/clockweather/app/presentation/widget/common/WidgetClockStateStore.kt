package com.clockweather.app.presentation.widget.common

import android.content.Context

/**
 * Stores the actual h1/h2/m1/m2 digit values last rendered on a widget.
 * Used by [WidgetDataBinder.bindClockViews] to compute an accurate incremental
 * diff even after Doze gaps (where arithmetic "minute - 1" would be wrong).
 */
data class DigitState(val h1: Int, val h2: Int, val m1: Int, val m2: Int) {
    companion object {
        fun from(hour: Int, minute: Int, is24h: Boolean): DigitState {
            val displayHour = if (is24h) hour
                else if (hour == 0) 12
                else if (hour > 12) hour - 12
                else hour
            return DigitState(displayHour / 10, displayHour % 10, minute / 10, minute % 10)
        }
    }
}

object WidgetClockStateStore {
    private const val PREFS_NAME = "widget_clock_state"
    private const val KEY_PREFIX = "last_rendered_epoch_minute_"
    private const val KEY_BASELINE_PREFIX = "baseline_ready_"
    private const val KEY_NO_ANIM_UNTIL_PREFIX = "no_anim_until_"
    private const val DIGITS_PREFIX = "digits_"

    fun getLastRenderedEpochMinute(context: Context, appWidgetId: Int): Long? {
        val prefs = prefs(context)
        val key = key(appWidgetId)
        return if (prefs.contains(key)) prefs.getLong(key, 0L) else null
    }

    fun markRendered(context: Context, appWidgetId: Int, epochMinute: Long) {
        prefs(context).edit().putLong(key(appWidgetId), epochMinute).apply()
    }

    fun clearWidget(context: Context, appWidgetId: Int) {
        prefs(context).edit()
            .remove(key(appWidgetId))
            .remove(baselineKey(appWidgetId))
            .remove(noAnimUntilKey(appWidgetId))
            .remove("${DIGITS_PREFIX}${appWidgetId}_h1")
            .remove("${DIGITS_PREFIX}${appWidgetId}_h2")
            .remove("${DIGITS_PREFIX}${appWidgetId}_m1")
            .remove("${DIGITS_PREFIX}${appWidgetId}_m2")
            .apply()
    }

    /** Clears only the stored digit state for a widget, forcing the next
     *  [updateWidget] call to treat it as a first render (full [updateAppWidget]).
     *  Used when settings change (theme, tile size) requires a full layout rebuild. */
    fun clearDigits(context: Context, appWidgetId: Int) {
        val key = "${DIGITS_PREFIX}${appWidgetId}"
        prefs(context).edit()
            .remove("${key}_h1")
            .remove("${key}_h2")
            .remove("${key}_m1")
            .remove("${key}_m2")
            .apply()
    }

    /** Persist the actual digit values that were last rendered so the next incremental
     *  tick can diff against them accurately (fixes Doze gap off-by-N digit flips). */
    fun saveLastDigits(context: Context, appWidgetId: Int, digits: DigitState) {
        val key = "${DIGITS_PREFIX}${appWidgetId}"
        prefs(context).edit()
            .putInt("${key}_h1", digits.h1)
            .putInt("${key}_h2", digits.h2)
            .putInt("${key}_m1", digits.m1)
            .putInt("${key}_m2", digits.m2)
            .apply()
    }

    /** Returns the last stored digits, or null if never stored for this widget. */
    fun getLastDigits(context: Context, appWidgetId: Int): DigitState? {
        val p = prefs(context)
        val key = "${DIGITS_PREFIX}${appWidgetId}"
        if (!p.contains("${key}_h1")) return null
        return DigitState(
            h1 = p.getInt("${key}_h1", 0),
            h2 = p.getInt("${key}_h2", 0),
            m1 = p.getInt("${key}_m1", 0),
            m2 = p.getInt("${key}_m2", 0)
        )
    }

    fun isBaselineReady(context: Context, appWidgetId: Int): Boolean {
        return prefs(context).getBoolean(baselineKey(appWidgetId), false)
    }

    fun markBaselineReady(context: Context, appWidgetId: Int) {
        prefs(context).edit().putBoolean(baselineKey(appWidgetId), true).apply()
    }

    fun markNoAnimationUntilEpochMinute(context: Context, appWidgetId: Int, epochMinute: Long) {
        prefs(context).edit().putLong(noAnimUntilKey(appWidgetId), epochMinute).apply()
    }

    fun shouldSuppressAnimation(context: Context, appWidgetId: Int, currentEpochMinute: Long): Boolean {
        val p = prefs(context)
        val key = noAnimUntilKey(appWidgetId)
        if (!p.contains(key)) return false
        val untilEpochMinute = p.getLong(key, -1L)
        if (currentEpochMinute <= untilEpochMinute) return true
        p.edit().remove(key).apply()
        return false
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun key(appWidgetId: Int) = "$KEY_PREFIX$appWidgetId"
    private fun baselineKey(appWidgetId: Int) = "$KEY_BASELINE_PREFIX$appWidgetId"
    private fun noAnimUntilKey(appWidgetId: Int) = "$KEY_NO_ANIM_UNTIL_PREFIX$appWidgetId"
}
