package com.asphalt.app.data

import android.content.Context
import android.os.SystemClock

/**
 * Tamper-resistant "now".
 *
 * The 24h work session is measured against Firestore SERVER time. Changing the phone's date/clock
 * therefore cannot extend a session: after every successful server sync we remember
 *   (serverTimeMs, elapsedRealtimeAtSync)
 * and compute now = serverTimeMs + (elapsedRealtime - elapsedAtSync). elapsedRealtime is a monotonic
 * counter that the user cannot change. The anchor is persisted so the accessibility service keeps
 * working after the process is restarted; it is discarded when a reboot is detected
 * (elapsedRealtime went backwards). Without a valid anchor the clock never goes earlier than the
 * last server time ever seen.
 */
object TrustedClock {
    private const val PREFS = "asphalt_trusted_clock"
    private const val K_SERVER = "server_ms"
    private const val K_ELAPSED = "elapsed_ms"

    @Volatile private var serverAnchorMs = 0L
    @Volatile private var elapsedAnchorMs = 0L
    @Volatile private var lastServerSeenMs = 0L
    @Volatile private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        val p = appContext!!.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val server = p.getLong(K_SERVER, 0L)
        val elapsed = p.getLong(K_ELAPSED, 0L)
        lastServerSeenMs = server
        // Reboot => elapsedRealtime restarted => stored anchor no longer comparable.
        if (server > 0L && SystemClock.elapsedRealtime() >= elapsed) {
            serverAnchorMs = server
            elapsedAnchorMs = elapsed
        }
    }

    /** Call with the server timestamp obtained from Firestore (never from the device clock). */
    fun anchor(serverTimeMs: Long) {
        if (serverTimeMs <= 0L) return
        val elapsed = SystemClock.elapsedRealtime()
        serverAnchorMs = serverTimeMs
        elapsedAnchorMs = elapsed
        lastServerSeenMs = maxOf(lastServerSeenMs, serverTimeMs)
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()
            ?.putLong(K_SERVER, serverTimeMs)?.putLong(K_ELAPSED, elapsed)?.apply()
    }

    val isAnchored: Boolean get() = serverAnchorMs > 0L

    fun now(): Long {
        val anchor = serverAnchorMs
        return if (anchor > 0L) {
            anchor + (SystemClock.elapsedRealtime() - elapsedAnchorMs)
        } else {
            maxOf(System.currentTimeMillis(), lastServerSeenMs)
        }
    }
}
