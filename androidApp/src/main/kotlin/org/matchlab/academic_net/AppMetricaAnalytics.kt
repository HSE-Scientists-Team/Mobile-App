package org.matchlab.academic_net

import android.util.Log
import io.appmetrica.analytics.AppMetrica
import io.appmetrica.analytics.AppMetricaConfig
import org.matchlab.academic_net.analytics.Analytics

/**
 * The configuration of the AppMetrica SDK for [apiKey] (the application key of the AppMetrica console, a UUID). The app
 * counts taps, so the extras that the SDK can collect on its own are switched off (what it records about sessions, the
 * device and the install stays, and is held back by the same sending switch):
 *
 * - **sending is off** from the start; [AppMetricaAnalytics.setEnabled] switches it on after the person's choice, so
 *   nothing leaves the device before it;
 * - no location, no advertising identifiers;
 * - no reports of crashes, native crashes or ANRs (the customer asked for the taps only; they can be turned on later);
 * - no tracking of the links that open the app: the link of the confirmation letter carries a single-use token, and
 *   it must not reach a third party;
 * - no automatic tracking of purchases (the app has none).
 *
 * The SDK log is on in debug builds ([debug]).
 *
 * @throws io.appmetrica.analytics.ValidationException when [apiKey] is not a UUID (the SDK refuses it)
 */
internal fun appMetricaConfig(apiKey: String, debug: Boolean): AppMetricaConfig =
    AppMetricaConfig.newConfigBuilder(apiKey)
        .withDataSendingEnabled(false)
        .withLocationTracking(false)
        .withAdvIdentifiersTracking(false)
        .withCrashReporting(false)
        .withNativeCrashReporting(false)
        .withAnrMonitoring(false)
        .withAppOpenTrackingEnabled(false)
        .withRevenueAutoTrackingEnabled(false)
        .apply { if (debug) withLogs() }
        .build()

/**
 * [Analytics] over the AppMetrica SDK. The SDK must be activated first (see [MatchLabApplication]); there is one
 * instance per process.
 *
 * **The SDK is not trusted to stay quiet.** Every call is wrapped: an exception of the SDK is logged and dropped, and
 * never reaches the app. [track] sends nothing while the person's choice is "off" ([setEnabled]); the events of that
 * time are dropped, not kept for later, so switching sending on does not release taps made before the choice. (What the
 * SDK itself records about sessions and the device is held back by the same switch: it sends nothing while it is off.)
 *
 * It starts with sending off, as the SDK was activated with it off.
 */
internal class AppMetricaAnalytics : Analytics {
    /** The person's choice as the last [setEnabled] set it; read on the threads of the taps. */
    @Volatile
    private var enabled = false

    override fun track(event: String, params: Map<String, String>) {
        if (!enabled) return
        guarded("report the event") {
            if (params.isEmpty()) AppMetrica.reportEvent(event) else AppMetrica.reportEvent(event, params)
        }
    }

    override fun setUserProfileId(id: String?) {
        guarded("set the user profile") { AppMetrica.setUserProfileID(id) }
    }

    override fun setEnabled(enabled: Boolean) {
        // The flag first: the events that are being tracked at this moment are stopped before the SDK is told.
        this.enabled = enabled
        guarded("switch sending") { AppMetrica.setDataSendingEnabled(enabled) }
    }

    /** Runs [call] and logs a failure of the SDK (never its data) instead of throwing it. */
    private inline fun guarded(what: String, call: () -> Unit) {
        try {
            call()
        } catch (error: Throwable) {
            Log.w(Tag, "AppMetrica failed to $what", error)
        }
    }

    private companion object {
        const val Tag = "MatchLabAnalytics"
    }
}
