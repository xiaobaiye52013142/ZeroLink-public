package com.helium4.localscreenshare

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.os.SystemClock
import com.helium4.localscreenshare.AppLogger
import androidx.annotation.RequiresApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val LOGTAG = "ZeroLink"

/**
 * Receiver side of Wi-Fi Direct. Joins the sender's group by requesting a network that
 * matches a [WifiNetworkSpecifier]. The returned [Network]'s socketFactory is used to route
 * the stream socket over the P2P link WITHOUT calling bindProcessToNetwork() — so the app's
 * other traffic (e.g. Firebase) keeps using the default network.
 */
@RequiresApi(Build.VERSION_CODES.Q)
class P2pConnection(context: Context) {

    private val cm = context.applicationContext
        .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private var callback: ConnectivityManager.NetworkCallback? = null

    companion object {
        /**
         * How long the join may take. This window has to cover the system's "Connect to
         * device?" dialog, which means it has to cover a human reading it, finding the right
         * network in the list and tapping Connect. The old 25 s budget was measured from
         * before the dialog was even shown, so a user who hesitated had the request cancelled
         * out from under them and saw a join failure they hadn't caused.
         */
        const val DEFAULT_JOIN_TIMEOUT_MS = 60_000L

        /**
         * Grace period on top of the framework timeout. requestNetwork's own timeout is the
         * one that matters; this only stops the coroutine hanging forever if the framework
         * never delivers any callback at all.
         */
        private const val WATCHDOG_GRACE_MS = 10_000L

        /** Below this, a failure came back too fast to have involved the approval dialog. */
        private const val NO_DIALOG_THRESHOLD_MS = 2_000L
    }

    /**
     * Suspends until the device joins [ssid], returning the bound [Network]. The callback is
     * kept registered until [close] so the system doesn't tear the network down mid-session.
     */
    suspend fun join(
        ssid: String,
        passphrase: String,
        timeoutMs: Long = DEFAULT_JOIN_TIMEOUT_MS
    ): Network =
        // The framework timeout below is the real deadline; this watchdog only fires if the
        // framework goes silent, so the two can no longer race over a slow user.
        withTimeout(timeoutMs + WATCHDOG_GRACE_MS) {
            suspendCancellableCoroutine { cont ->
                val specifier = WifiNetworkSpecifier.Builder()
                    .setSsid(ssid)
                    .setWpa2Passphrase(passphrase)
                    .build()
                val request = NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .setNetworkSpecifier(specifier)
                    .build()
                val startedAt = SystemClock.elapsedRealtime()
                fun elapsed() = SystemClock.elapsedRealtime() - startedAt

                val cb = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        AppLogger.i(LOGTAG, "[P2P] Joined ssid=$ssid network=$network after ${elapsed()}ms")
                        if (cont.isActive) cont.resume(network)
                    }

                    override fun onUnavailable() {
                        val ms = elapsed()
                        // onUnavailable has only two documented causes: the user denied the
                        // request, or the connection failed. The elapsed time is what tells
                        // them apart, so log it prominently rather than listing every cause.
                        val hint = when {
                            ms < NO_DIALOG_THRESHOLD_MS ->
                                "failed too fast for the approval dialog to have been shown — " +
                                    "the request itself was rejected (check the SSID/passphrase in the QR payload)"
                            ms >= timeoutMs - 1_000 ->
                                "ran the full ${timeoutMs}ms timeout — the dialog was almost certainly " +
                                    "shown but the sender's group never appeared in it, or nobody tapped Connect"
                            else ->
                                "failed mid-way — the dialog was shown and either the user denied it " +
                                    "or association with the group failed (wrong passphrase, or a 5GHz-only group)"
                        }
                        AppLogger.e(LOGTAG, "[P2P] Join unavailable for ssid=$ssid after ${ms}ms: $hint")
                        close()
                        if (cont.isActive) {
                            cont.resumeWithException(
                                IOException("Wi-Fi Direct join failed for ssid=$ssid after ${ms}ms")
                            )
                        }
                    }

                    override fun onLost(network: Network) {
                        // Not a join failure on its own, but it dates the moment the group went
                        // away, which is what distinguishes "never joined" from "joined then dropped".
                        AppLogger.w(LOGTAG, "[P2P] Network lost for ssid=$ssid after ${elapsed()}ms")
                    }
                }
                callback = cb
                AppLogger.i(
                    LOGTAG,
                    "[P2P] Requesting network ssid=$ssid passLen=${passphrase.length} timeout=${timeoutMs}ms"
                )
                // Hand the deadline to the framework instead of enforcing it here: it delivers
                // onUnavailable on expiry, so every failure now arrives through one path with
                // a measured duration attached.
                cm.requestNetwork(request, cb, timeoutMs.toInt())
                cont.invokeOnCancellation {
                    AppLogger.w(LOGTAG, "[P2P] Join cancelled for ssid=$ssid after ${elapsed()}ms")
                    close()
                }
            }
        }

    fun close() {
        callback?.let { try { cm.unregisterNetworkCallback(it) } catch (_: Exception) {} }
        callback = null
    }
}
