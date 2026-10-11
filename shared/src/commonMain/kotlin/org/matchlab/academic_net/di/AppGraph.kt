package org.matchlab.academic_net.di

/**
 * Holds the one [AppComponent] of the process. It outlives activities, so the mock data keeps what
 * the user has changed on rotation.
 */
internal object AppGraph {
    val component: AppComponent by lazy { createAppComponent() }
}
