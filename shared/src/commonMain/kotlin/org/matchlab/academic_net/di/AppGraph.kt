package org.matchlab.academic_net.di

import org.matchlab.academic_net.securestore.SecureStore

/**
 * Holds the one [AppComponent] of the process. It outlives activities, so the mock data keeps what
 * the user has changed on rotation. The secure store is created by the platform on the first use.
 */
internal object AppGraph {
    private var instance: AppComponent? = null

    fun component(secureStore: () -> SecureStore): AppComponent =
        instance ?: createAppComponent(secureStore()).also { instance = it }
}
