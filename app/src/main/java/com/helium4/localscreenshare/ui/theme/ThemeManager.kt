package com.helium4.localscreenshare.ui.theme

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.helium4.localscreenshare.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.MonthDay

private const val LOGTAG = "ZeroLink"
private const val PREFS = "zerolink_theme"
private const val KEY_CACHED_THEME = "cached_theme"
private const val KEY_CACHED_FORCE_DARK = "cached_force_dark"

const val RC_ACTIVE_THEME = "active_theme"
const val RC_FORCE_DARK = "force_dark"

/** Where the currently-applied theme id came from — used for analytics. */
enum class ThemeSource { CACHE, REMOTE, SEASONAL, DEBUG_OVERRIDE }

/**
 * Owns theme selection for the whole app.
 *
 * Cold start reads the last known id straight out of SharedPreferences so the first frame is
 * already correct, then a non-blocking Remote Config fetch updates [themeId] if the server
 * disagrees. Seasonal themes apply automatically unless Remote Config names a theme explicitly.
 */
object ThemeManager {

    private val _themeId = MutableStateFlow(ThemeRegistry.DEFAULT)
    val themeId: StateFlow<String> = _themeId.asStateFlow()

    /** null = follow the system setting; true/false = force. */
    private val _forceDark = MutableStateFlow<Boolean?>(null)
    val forceDark: StateFlow<Boolean?> = _forceDark.asStateFlow()

    private var appContext: Context? = null
    private var debugOverride: String? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        // 1. Cached value first so the very first composition is already themed.
        val cached = prefs.getString(KEY_CACHED_THEME, null)
        if (cached != null) {
            _themeId.value = cached
            _forceDark.value = when (prefs.getInt(KEY_CACHED_FORCE_DARK, -1)) {
                0 -> false; 1 -> true; else -> null
            }
            logSwitch(cached, ThemeSource.CACHE)
        } else {
            // 2. No cache — a seasonal window still beats the plain default.
            seasonalThemeFor(LocalDate.now())?.let {
                _themeId.value = it
                logSwitch(it, ThemeSource.SEASONAL)
            }
        }

        refresh()
    }

    /** Non-blocking Remote Config fetch. Safe to call on every app foreground. */
    fun refresh() {
        val config = FirebaseRemoteConfig.getInstance()
        config.fetchAndActivate().addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                AppLogger.w(LOGTAG, "[Theme] Remote Config fetch failed, keeping '${_themeId.value}'")
                return@addOnCompleteListener
            }
            val remote = config.getString(RC_ACTIVE_THEME).trim()
            val remoteForceDark = config.getString(RC_FORCE_DARK).trim().lowercase()

            // An explicit, known remote id always wins. Otherwise a seasonal window applies,
            // and failing both we keep whatever the default is.
            val resolved: String
            val source: ThemeSource
            if (remote.isNotEmpty() && remote in ThemeRegistry.allThemeIds && remote != ThemeRegistry.DEFAULT) {
                resolved = remote
                source = ThemeSource.REMOTE
            } else {
                val seasonal = seasonalThemeFor(LocalDate.now())
                if (seasonal != null) {
                    resolved = seasonal
                    source = ThemeSource.SEASONAL
                } else {
                    resolved = remote.ifEmpty { ThemeRegistry.DEFAULT }
                    source = ThemeSource.REMOTE
                }
            }

            val dark: Boolean? = when (remoteForceDark) {
                "true" -> true
                "false" -> false
                else -> null
            }

            if (resolved != _themeId.value || dark != _forceDark.value) {
                _themeId.value = resolved
                _forceDark.value = dark
                cache(resolved, dark)
                logSwitch(resolved, source)
            }
        }
    }

    /** Debug theme switcher — cycles to the next theme in the registry. */
    fun cycleDebugTheme() {
        val current = debugOverride ?: _themeId.value
        val idx = ThemeRegistry.allThemeIds.indexOf(current)
        val next = ThemeRegistry.allThemeIds[(idx + 1) % ThemeRegistry.allThemeIds.size]
        debugOverride = next
        _themeId.value = next
        logSwitch(next, ThemeSource.DEBUG_OVERRIDE)
    }

    fun setDebugTheme(id: String) {
        debugOverride = id
        _themeId.value = id
        logSwitch(id, ThemeSource.DEBUG_OVERRIDE)
    }

    fun clearDebugOverride() {
        debugOverride = null
        refresh()
    }

    private fun cache(id: String, dark: Boolean?) {
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()
            ?.putString(KEY_CACHED_THEME, id)
            ?.putInt(KEY_CACHED_FORCE_DARK, when (dark) { true -> 1; false -> 0; null -> -1 })
            ?.apply()
    }

    private fun logSwitch(id: String, source: ThemeSource) {
        val timestamp = System.currentTimeMillis()
        AppLogger.i(LOGTAG, "[Theme] Applied '$id' (source=$source) at $timestamp")
        // Wrapped: Analytics is unavailable if FirebaseApp hasn't initialised yet, and a missed
        // analytics event must never stop the theme from being applied.
        try {
            FirebaseAnalytics.getInstance(appContext ?: return).logEvent(
                "theme_applied",
                Bundle().apply {
                    putString("theme_name", id)
                    putString("source", source.name.lowercase())
                    putLong("timestamp", timestamp)
                }
            )
        } catch (e: Exception) {
            AppLogger.w(LOGTAG, "[Theme] Analytics logEvent failed: ${e.message}")
        }
    }

    // ── Seasonal windows ─────────────────────────────────────────────────────

    /** Returns a seasonal theme id if [date] falls inside one of the windows, else null. */
    fun seasonalThemeFor(date: LocalDate): String? {
        // Christmas — Dec 20..26
        val md = MonthDay.from(date)
        if (md >= MonthDay.of(12, 20) && md <= MonthDay.of(12, 26)) return ThemeRegistry.CHRISTMAS

        // Easter Sunday ±3 days
        val easter = easterSunday(date.year)
        if (!date.isBefore(easter.minusDays(3)) && !date.isAfter(easter.plusDays(3))) {
            return ThemeRegistry.EASTER
        }

        // Eid al-Fitr / al-Adha ±2 days
        eidDatesFor(date.year).forEach { eid ->
            if (!date.isBefore(eid.minusDays(2)) && !date.isAfter(eid.plusDays(2))) {
                return ThemeRegistry.EID
            }
        }
        return null
    }

    /** Anonymous Gregorian algorithm (Meeus/Jones/Butcher) — exact for any Gregorian year. */
    fun easterSunday(year: Int): LocalDate {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val day = ((h + l - 7 * m + 114) % 31) + 1
        return LocalDate.of(year, month, day)
    }

    /**
     * Approximate Gregorian dates for Eid al-Fitr and Eid al-Adha.
     *
     * Precise dates depend on lunar observation and vary by region, so these are the widely
     * published astronomical estimates. Values outside the table fall back to drifting the
     * nearest known year by ~10.87 days per year, which stays within the ±2-day window for a
     * few years either side. Extend the table as authoritative dates are published.
     */
    fun eidDatesFor(year: Int): List<LocalDate> {
        knownEidDates[year]?.let { return it }

        val anchorYear = knownEidDates.keys.minByOrNull { kotlin.math.abs(it - year) } ?: return emptyList()
        val anchor = knownEidDates[anchorYear] ?: return emptyList()
        val driftDays = ((anchorYear - year) * 10.87).toLong()   // Hijri year is ~10.87 days shorter
        return anchor.map { it.plusDays(driftDays) }
    }

    private val knownEidDates: Map<Int, List<LocalDate>> = mapOf(
        2024 to listOf(LocalDate.of(2024, 4, 10), LocalDate.of(2024, 6, 16)),
        2025 to listOf(LocalDate.of(2025, 3, 30), LocalDate.of(2025, 6, 6)),
        2026 to listOf(LocalDate.of(2026, 3, 20), LocalDate.of(2026, 5, 27)),
        2027 to listOf(LocalDate.of(2027, 3, 9),  LocalDate.of(2027, 5, 16)),
        2028 to listOf(LocalDate.of(2028, 2, 26), LocalDate.of(2028, 5, 5)),
        2029 to listOf(LocalDate.of(2029, 2, 14), LocalDate.of(2029, 4, 24)),
        2030 to listOf(LocalDate.of(2030, 2, 4),  LocalDate.of(2030, 4, 13)),
    )
}
