package org.matchlab.academic_net.analytics

/**
 * Usage analytics as the shared code sees it: three calls and no SDK. The platform implements it over its own SDK
 * (Android: AppMetrica, in `:androidApp`; iOS: a Swift class in `iosApp/`) and hands it to `App(analytics = …)`.
 * An app without a platform implementation (a build without an application key, the tests, the iOS app until its SDK
 * is connected) runs with [NoOpAnalytics].
 *
 * **Analytics is never a reason to fail.** No call throws and none waits for the network: the SDK sends the events in
 * batches by itself, and an implementation catches the exceptions of its SDK.
 *
 * **Nothing is sent before the app allows it.** An implementation starts with sending switched off, and [setEnabled] is
 * the only thing that switches it on: with the person's "yes", or by the default of "on" while somebody is signed in and
 * has not chosen (see [StatisticsPreference] for where the choice is kept and `AnalyticsController` in `:shared` for who
 * applies it).
 *
 * **What may be passed.** Fixed event names ([AnalyticsEvents]), fixed `source` words ([ProjectSource], [UserSource])
 * and the ids the server gave to projects and people. Never names, e-mail addresses, message or profile texts, titles
 * or links.
 */
interface Analytics {
    /**
     * Sends the event [event] with the string [params]. Never throws, and does nothing while sending is off: such an
     * event is dropped, not kept until sending is switched on.
     */
    fun track(event: String, params: Map<String, String> = emptyMap())

    /**
     * Ties the next events to the account [id] (the AppMetrica user profile id), or to nobody when it is `null` (the
     * person signed out). Sends no event by itself.
     */
    fun setUserProfileId(id: String?)

    /**
     * Whether sending is allowed: `false` stops every sending (and drops what [track] gets), `true` allows it. It is the
     * person's choice, or the default of "on" while somebody is signed in and nobody has chosen (see `AnalyticsController`
     * in `:shared`). Called when the app has read the choice and with every change of it.
     */
    fun setEnabled(enabled: Boolean)
}

/** [Analytics] that does nothing: the implementation of builds without an application key, of tests and of previews. */
object NoOpAnalytics : Analytics {
    override fun track(event: String, params: Map<String, String>) = Unit

    override fun setUserProfileId(id: String?) = Unit

    override fun setEnabled(enabled: Boolean) = Unit
}
