package org.matchlab.academic_net.di

import me.tatarka.inject.annotations.Scope

/**
 * Lives as long as the process: what is scoped with it (the Gateway client, the data source and the repository of the
 * sign-in) is created once, so the state of the session (signed in or not) survives a configuration change and is the
 * same for every screen. What belongs to one signed-in user is [SessionScope]d instead.
 */
@Scope
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER)
internal annotation class AppScope
