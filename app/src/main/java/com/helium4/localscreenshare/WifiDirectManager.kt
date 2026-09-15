package com.helium4.localscreenshare

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.WifiP2pManager.ActionListener
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.helium4.localscreenshare.AppLogger

private const val LOGTAG = "ZeroLink"

/**
 * Sender side of Wi-Fi Direct. Creates an autonomous Group Owner so the receiver can join
 * with a single standard system dialog (via WifiNetworkSpecifier) and the GO IP is
 * deterministic (~192.168.49.1). The existing ServerSocket already listens on all
 * interfaces, so nothing else about the wire protocol changes.
 *
 * createGroup's ActionListener.onSuccess only means the framework ACCEPTED the request —
 * SSID/passphrase and the p2p interface appear a second or two later. So readiness is driven
 * by the WIFI_P2P_CONNECTION_CHANGED_ACTION broadcast (fast path), with a polled
 * requestGroupInfo backstop for OEMs that don't deliver that broadcast reliably.
 */
class WifiDirectManager(private val context: Context) {

    data class GroupInfo(val ssid: String, val passphrase: String, val ownerIp: String)

    private val manager: WifiP2pManager? =
        context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private var channel: WifiP2pManager.Channel? = null
    // Guards the async createGroup chain so a stop() mid-flight can't bring a group back up.
    private var active = false
    // Set once onReady/onError has fired, so the broadcast and the poller can't both deliver.
    private var settled = false
    private val handler = Handler(Looper.getMainLooper())
    private var createAttempts = 0
    // True while exactly one pollGroupInfo chain is in flight. Without this, every
    // groupFormed broadcast started another chain and they all raced to exhaust the budget.
    private var polling = false
    // Instant after which formation is given up on. A deadline, not an attempt count, so
    // extra ticks from any source can never shorten the real waiting period.
    private var formDeadlineMs = 0L
    private var receiver: BroadcastReceiver? = null
    private var onReadyCb: ((GroupInfo) -> Unit)? = null
    private var onErrorCb: ((String) -> Unit)? = null

    companion object {
        /** Whether this device has Wi-Fi Direct hardware/framework support at all. */
        fun isSupported(context: Context): Boolean =
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT)

        private const val MAX_CREATE_ATTEMPTS = 3
        // The framework needs a beat after removeGroup before it will accept createGroup,
        // otherwise it reports BUSY. Give it time, and back off further on each retry.
        private const val SETTLE_MS = 900L
        private const val RETRY_BASE_MS = 1500L
        // Group formation (GO negotiation + interface up) takes a second or two after
        // createGroup is accepted. Poll requestGroupInfo across this window rather than
        // reading it once and treating a null as fatal.
        private const val INFO_RETRY_MS = 600L
        // How long a group gets to form once createGroup has been accepted.
        private const val GROUP_FORM_TIMEOUT_MS = 15_000L
        // Overall cap on start() -> onReady/onError. OEMs do drop the removeGroup and
        // createGroup listeners; without this the UI spinner would never resolve.
        private const val OVERALL_TIMEOUT_MS = 25_000L
    }

    /**
     * Starts a P2P group. [onReady] is called on the main thread with the SSID, passphrase
     * and group-owner IP once the group has formed; [onError] with a user-facing message.
     */
    @SuppressLint("MissingPermission")
    fun start(onReady: (GroupInfo) -> Unit, onError: (String) -> Unit) {
        val mgr = manager
        // Plenty of tablets and low-end phones ship without P2P at all. The service object can
        // still be non-null on those, and createGroup then fails late - or reports success on a
        // group no peer can join - so check the hardware feature before promising anything.
        if (mgr == null || !isSupported(context)) {
            onError("Wi-Fi Direct isn't available on this device - use same-Wi-Fi pairing instead")
            return
        }
        // Wi-Fi Direct silently reports BUSY/ERROR when its real prerequisites aren't met.
        // Check them up front so the user gets something they can act on.
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        if (wifi?.isWifiEnabled != true) {
            onError("Turn Wi-Fi on (you don't need to join a network) then try again")
            return
        }
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2 && !isLocationEnabled()) {
            onError("Turn on Location in system settings — Wi-Fi Direct needs it on this Android version")
            return
        }

        active = true
        settled = false
        createAttempts = 0
        polling = false
        formDeadlineMs = 0L
        onReadyCb = onReady
        onErrorCb = onError
        // A null ChannelListener leaves a lost channel undetectable, so every later call would
        // fail silently. Surface it as a retryable error and drop the dead channel instead.
        val ch = channel ?: mgr.initialize(context, Looper.getMainLooper()) {
            AppLogger.w(LOGTAG, "[WifiDirect] Channel disconnected")
            channel = null
            if (active && !settled) fail("Wi-Fi Direct was interrupted. Try again.")
        }.also { channel = it }
        registerP2pReceiver()
        handler.postDelayed({
            if (active && !settled) {
                AppLogger.e(LOGTAG, "[WifiDirect] No framework response within ${OVERALL_TIMEOUT_MS}ms")
                fail("Wi-Fi Direct didn't respond. Turn Wi-Fi off and on, then try again.")
            }
        }, OVERALL_TIMEOUT_MS)
        // Remove any stale group we (or the system) may have left behind, then create fresh
        // after a short settle delay.
        mgr.removeGroup(ch, object : ActionListener {
            override fun onSuccess() = scheduleCreate(mgr, ch, SETTLE_MS)
            override fun onFailure(reason: Int) = scheduleCreate(mgr, ch, SETTLE_MS)
        })
    }

    /**
     * Listens for the group-formed event. This is the authoritative signal that SSID and
     * passphrase exist; [pollGroupInfo] is the backstop for devices that don't deliver it.
     */
    private fun registerP2pReceiver() {
        if (receiver != null) return
        val rx = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action != WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION) return
                if (!active || settled) return
                @Suppress("DEPRECATION")
                val info = intent.getParcelableExtra<WifiP2pInfo>(WifiP2pManager.EXTRA_WIFI_P2P_INFO)
                if (info?.groupFormed != true) return
                val group = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra<WifiP2pGroup>(WifiP2pManager.EXTRA_WIFI_P2P_GROUP)
                } else null
                AppLogger.i(LOGTAG, "[WifiDirect] groupFormed broadcast, isGroupOwner=${info.isGroupOwner}")
                // The group extra is usually already populated. If it isn't, the poller is
                // already running and will pick the credentials up on its next tick.
                if (!deliver(group?.networkName, group?.passphrase, info.groupOwnerAddress?.hostAddress)) {
                    // Credentials not in the extra yet — just make sure a poll chain is
                    // running. startPolling() is idempotent, so repeat broadcasts are free.
                    startPolling()
                }
            }
        }
        receiver = rx
        ContextCompat.registerReceiver(
            context.applicationContext,
            rx,
            IntentFilter(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private fun unregisterP2pReceiver() {
        receiver?.let {
            try { context.applicationContext.unregisterReceiver(it) } catch (_: Exception) {}
        }
        receiver = null
    }

    private fun scheduleCreate(mgr: WifiP2pManager, ch: WifiP2pManager.Channel, delayMs: Long) {
        if (!active) return
        handler.postDelayed({ if (active) createGroup(mgr, ch) }, delayMs)
    }

    @SuppressLint("MissingPermission")
    private fun createGroup(mgr: WifiP2pManager, ch: WifiP2pManager.Channel) {
        mgr.createGroup(ch, object : ActionListener {
            override fun onSuccess() {
                // Request accepted — NOT yet formed. Start polling; the broadcast may beat us.
                AppLogger.i(LOGTAG, "[WifiDirect] createGroup accepted, waiting for group to form")
                if (active) startPolling()
            }

            override fun onFailure(reason: Int) {
                if (!active) return
                // BUSY usually means a previous P2P operation/group hasn't cleared yet. Clear it
                // again and retry with backoff before giving up.
                if (reason == WifiP2pManager.BUSY && createAttempts < MAX_CREATE_ATTEMPTS) {
                    createAttempts++
                    AppLogger.w(LOGTAG, "[WifiDirect] createGroup BUSY, retry $createAttempts")
                    try { mgr.removeGroup(ch, null) } catch (_: Exception) {}
                    scheduleCreate(mgr, ch, RETRY_BASE_MS * createAttempts)
                    return
                }
                val reasonName = when (reason) {
                    WifiP2pManager.P2P_UNSUPPORTED -> "P2P_UNSUPPORTED"
                    WifiP2pManager.BUSY -> "BUSY"
                    WifiP2pManager.ERROR -> "ERROR"
                    else -> "UNKNOWN($reason)"
                }
                val msg = when (reason) {
                    WifiP2pManager.P2P_UNSUPPORTED -> "Wi-Fi Direct isn't supported on this device"
                    WifiP2pManager.BUSY ->
                        "Wi-Fi Direct is busy. Turn Wi-Fi off and on, then try again."
                    WifiP2pManager.ERROR ->
                        "Wi-Fi Direct couldn't start. Turn Wi-Fi off and on, then try again."
                    else -> "Couldn't start Wi-Fi Direct (code $reason)"
                }
                AppLogger.e(LOGTAG, "[WifiDirect] createGroup failed after $createAttempts attempt(s): reason=$reasonName ($reason) — $msg")
                fail(msg)
            }
        })
    }

    /**
     * Starts the single group-info poll chain, arming the formation deadline on first call.
     * Idempotent: createGroup's listener and every groupFormed broadcast can call this freely
     * without multiplying the number of in-flight chains.
     */
    private fun startPolling() {
        if (!active || settled || polling) return
        polling = true
        formDeadlineMs = SystemClock.elapsedRealtime() + GROUP_FORM_TIMEOUT_MS
        handler.postDelayed({ pollGroupInfo() }, INFO_RETRY_MS)
    }

    /**
     * Asks the framework for the group's credentials. An empty/null answer means the group
     * hasn't finished forming yet, so this re-arms itself until [formDeadlineMs] passes.
     */
    @SuppressLint("MissingPermission")
    private fun pollGroupInfo() {
        if (!active || settled) { polling = false; return }
        val mgr = manager
        val ch = channel
        if (mgr == null || ch == null) { polling = false; return }
        mgr.requestGroupInfo(ch) { group ->
            if (!active || settled) { polling = false; return@requestGroupInfo }
            val ssid = group?.networkName
            val pass = group?.passphrase
            if (ssid.isNullOrEmpty() || pass.isNullOrEmpty()) {
                if (SystemClock.elapsedRealtime() >= formDeadlineMs) {
                    polling = false
                    val detail = when {
                        group == null -> "requestGroupInfo kept returning null (group never formed)"
                        ssid.isNullOrEmpty() -> "group exists but SSID stayed null/empty"
                        else -> "group exists but passphrase stayed null/empty"
                    }
                    AppLogger.e(LOGTAG, "[WifiDirect] Group never became usable within ${GROUP_FORM_TIMEOUT_MS}ms: $detail")
                    fail("Wi-Fi Direct group didn't come up. Turn Wi-Fi off and on, then try again.")
                } else {
                    handler.postDelayed({ pollGroupInfo() }, INFO_RETRY_MS)
                }
                return@requestGroupInfo
            }
            mgr.requestConnectionInfo(ch) { info ->
                deliver(ssid, pass, info?.groupOwnerAddress?.hostAddress)
            }
        }
    }

    /** Fires [onReadyCb] exactly once. Returns false if the credentials weren't usable yet. */
    private fun deliver(ssid: String?, pass: String?, ownerIp: String?): Boolean {
        if (!active || settled) return true
        if (ssid.isNullOrEmpty() || pass.isNullOrEmpty()) return false
        settled = true
        polling = false
        // Nothing further is driven off the broadcast, the poll chain or the overall
        // watchdog once the credentials are in hand.
        handler.removeCallbacksAndMessages(null)
        unregisterP2pReceiver()
        // The GO address is fixed by the framework; the literal is only a belt-and-braces
        // fallback for devices that report a null groupOwnerAddress on a client-less group.
        val ip = ownerIp ?: "192.168.49.1"
        AppLogger.i(LOGTAG, "[WifiDirect] Ready ssid=$ssid ip=$ip")
        onReadyCb?.invoke(GroupInfo(ssid, pass, ip))
        return true
    }

    /** Fires [onErrorCb] exactly once and tears the half-built group down. */
    private fun fail(msg: String) {
        if (settled) return
        settled = true
        active = false
        polling = false
        handler.removeCallbacksAndMessages(null)
        unregisterP2pReceiver()
        val mgr = manager
        val ch = channel
        if (mgr != null && ch != null) {
            try { mgr.removeGroup(ch, null) } catch (_: Exception) {}
        }
        onErrorCb?.invoke(msg)
    }

    private fun isLocationEnabled(): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return true
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            lm.isLocationEnabled
        } else {
            @Suppress("DEPRECATION")
            try {
                Settings.Secure.getInt(context.contentResolver, Settings.Secure.LOCATION_MODE) !=
                    Settings.Secure.LOCATION_MODE_OFF
            } catch (_: Exception) {
                true
            }
        }
    }

    fun stop() {
        active = false
        settled = true
        polling = false
        handler.removeCallbacksAndMessages(null)
        unregisterP2pReceiver()
        onReadyCb = null
        onErrorCb = null
        val mgr = manager
        val ch = channel
        if (mgr != null && ch != null) {
            try { mgr.removeGroup(ch, null) } catch (_: Exception) {}
            // Release the framework channel (Closeable since API 27) instead of leaking it
            // across repeated toggles; a later start() re-initializes a fresh one.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                try { ch.close() } catch (_: Exception) {}
            }
        }
        channel = null
    }
}
