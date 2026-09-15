package com.helium4.localscreenshare

import android.content.Context
import java.security.SecureRandom

/**
 * Sender-side pairing PIN policy.
 *
 * By default each session gets a fresh random PIN, which is the safer choice. A user who pairs
 * the same two devices constantly can opt into a fixed PIN instead, so the receiver can
 * remember it and reconnect in one tap (see [DeviceStore]) — at the cost of the PIN no longer
 * rotating between sessions.
 */
object PinStore {

    private const val PREFS = "zerolink_pin"
    private const val KEY_STATIC_ENABLED = "static_pin_enabled"
    private const val KEY_STATIC_PIN = "static_pin"

    const val PIN_LENGTH = 6

    // SecureRandom, not Kotlin's default Random: the PIN is the only credential gating screen
    // capture and remote control, so it must not be predictable from previously observed PINs.
    private val random = SecureRandom()

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** A fresh cryptographically-random 6-digit PIN (100000..999999 inclusive). */
    fun generate(): String = (random.nextInt(900_000) + 100_000).toString()

    fun isStaticEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_STATIC_ENABLED, false)

    fun setStaticEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_STATIC_ENABLED, enabled).apply()
        // Materialize a PIN on first enable so the UI always has something to show.
        if (enabled) staticPin(context)
    }

    /** The saved fixed PIN, generating and persisting one the first time it's needed. */
    fun staticPin(context: Context): String {
        val p = prefs(context)
        val existing = p.getString(KEY_STATIC_PIN, null)
        if (isValid(existing)) return existing!!
        val fresh = generate()
        p.edit().putString(KEY_STATIC_PIN, fresh).apply()
        return fresh
    }

    fun setStaticPin(context: Context, pin: String) {
        prefs(context).edit().putString(KEY_STATIC_PIN, pin).apply()
    }

    fun isValid(pin: String?): Boolean =
        pin != null && pin.length == PIN_LENGTH && pin.all { it.isDigit() }

    /**
     * The PIN to use for a session that is starting now: the fixed one when the user has
     * enabled that, otherwise a fresh random one.
     */
    fun pinForSession(context: Context): String =
        if (isStaticEnabled(context)) staticPin(context) else generate()
}

/**
 * Receiver-side memory of devices already paired with, so a known sender can be reconnected
 * in a single tap instead of re-typing its PIN. Only useful against a sender using a fixed
 * PIN — a rotating PIN will not match next session, which is why [DeviceStore.savedPin] is
 * treated as a hint the user can override rather than a guarantee.
 */
object DeviceStore {

    private const val PREFS = "zerolink_devices"
    private const val KEY_LAST_NAME = "last_name"
    private const val KEY_LAST_IP = "last_ip"
    private const val PIN_PREFIX = "pin_"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun lastName(context: Context): String? = prefs(context).getString(KEY_LAST_NAME, null)

    fun savedPin(context: Context, deviceName: String): String? =
        prefs(context).getString(PIN_PREFIX + deviceName, null)
            ?.takeIf { PinStore.isValid(it) }

    /** Records a device and the PIN that was used for it, for one-tap reconnection. */
    fun remember(context: Context, deviceName: String, ip: String, pin: String) {
        prefs(context).edit()
            .putString(KEY_LAST_NAME, deviceName)
            .putString(KEY_LAST_IP, ip)
            .putString(PIN_PREFIX + deviceName, pin)
            .apply()
    }

    /** Drops just the remembered PIN, so the next tap asks for one again. */
    fun forgetPin(context: Context, deviceName: String) {
        prefs(context).edit().remove(PIN_PREFIX + deviceName).apply()
    }

    /** Device name -> remembered PIN, for every device paired with so far. */
    fun allPins(context: Context): Map<String, String> =
        prefs(context).all
            .filterKeys { it.startsWith(PIN_PREFIX) }
            .mapNotNull { (k, v) ->
                val pin = v as? String ?: return@mapNotNull null
                if (PinStore.isValid(pin)) k.removePrefix(PIN_PREFIX) to pin else null
            }
            .toMap()
}
