package org.matchlab.academic_net

import android.app.Application
import android.util.Log
import io.appmetrica.analytics.AppMetrica
import org.matchlab.academic_net.analytics.Analytics
import org.matchlab.academic_net.analytics.NoOpAnalytics

/**
 * The application of the process. It activates the analytics here, before any activity exists, as the AppMetrica SDK
 * asks, and keeps the one [analytics] of the process for the activities to hand to [App].
 */
class MatchLabApplication : Application() {
    /**
     * The analytics of this process: AppMetrica when the build has an application key (the Gradle property
     * `matchlab.appmetricaKey` or `local.properties`, see `androidApp/build.gradle.kts`), otherwise [NoOpAnalytics],
     * which is also what a refusal of the SDK to start leaves. Either way nothing is sent until the app switches it on.
     */
    var analytics: Analytics = NoOpAnalytics
        private set

    override fun onCreate() {
        super.onCreate()
        analytics = activateAnalytics(BuildConfig.APPMETRICA_API_KEY, BuildConfig.DEBUG)
    }

    /** Activates the SDK with [apiKey] and returns the analytics over it; a blank key or a failing SDK gives none. */
    private fun activateAnalytics(apiKey: String, debug: Boolean): Analytics {
        if (apiKey.isBlank()) return NoOpAnalytics
        return try {
            AppMetrica.activate(this, appMetricaConfig(apiKey, debug))
            AppMetricaAnalytics()
        } catch (error: Throwable) {
            // The app works without statistics; a start that fails because of the SDK must not take the app with it.
            Log.w("MatchLabAnalytics", "AppMetrica was not activated", error)
            NoOpAnalytics
        }
    }
}
