package com.helium4.localscreenshare

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * Reports whether the phone currently has a *real* Wi-Fi internet uplink. Used to learn,
 * empirically, whether starting a Wi-Fi Direct group tears down the phone's station-mode
 * Wi-Fi (single-radio) or coexists with it (dual-radio).
 *
 * A Wi-Fi Direct group's `p2p0` interface is neither validated nor internet-bearing, so
 * requiring INTERNET + VALIDATED cleanly ignores the group and reflects only the STA uplink.
 */
class WifiRadioProbe(context: Context) {

    private val cm = context.applicationContext
        .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    fun hasValidatedWifiInternet(): Boolean {
        return try {
            cm.allNetworks.any { network ->
                val caps = cm.getNetworkCapabilities(network) ?: return@any false
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            }
        } catch (_: Exception) {
            false
        }
    }
}
