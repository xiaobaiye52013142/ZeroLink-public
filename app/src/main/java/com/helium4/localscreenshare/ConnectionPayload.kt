package com.helium4.localscreenshare

import org.json.JSONObject

/**
 * Everything a receiver needs to auto-join a sender over Wi-Fi Direct and authenticate.
 * Serialized as versioned JSON and carried inside the pairing QR code.
 */
data class ConnectionPayload(
    val ssid: String,
    val pass: String,
    val ip: String,
    val port: Int,
    val pin: String
) {
    fun toJson(): String = JSONObject().apply {
        put("v", VERSION)
        put("ssid", ssid)
        put("pass", pass)
        put("ip", ip)
        put("port", port)
        put("pin", pin)
    }.toString()

    companion object {
        private const val VERSION = 1

        fun fromJson(raw: String): ConnectionPayload? = try {
            val o = JSONObject(raw)
            if (o.optInt("v") != VERSION) null
            else ConnectionPayload(
                ssid = o.getString("ssid"),
                pass = o.getString("pass"),
                ip = o.getString("ip"),
                port = o.optInt("port", 5000),
                pin = o.getString("pin")
            )
        } catch (e: Exception) {
            null
        }
    }
}
