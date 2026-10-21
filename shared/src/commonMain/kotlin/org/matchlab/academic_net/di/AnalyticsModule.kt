package org.matchlab.academic_net.di

import me.tatarka.inject.annotations.Provides
import org.matchlab.academic_net.analytics.StatisticsPreference
import org.matchlab.academic_net.securestore.SecureStore

/**
 * The analytics, for the whole process. The [org.matchlab.academic_net.analytics.Analytics] itself comes from the
 * platform (a parameter of the component, see [AppComponent]); what the shared code adds is the choice of the person.
 */
internal interface AnalyticsModule {
    /**
     * The choice "send usage statistics" of this device, in the [SecureStore] of the platform. One for the process: the
     * analytics controller, the settings and the sign-up form all read and change the same value. It is the choice of
     * the device, so it is not part of any session graph and survives a sign-out.
     */
    @Provides
    @AppScope
    fun statisticsPreference(store: SecureStore): StatisticsPreference = StatisticsPreference(store)
}
