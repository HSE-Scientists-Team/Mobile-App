package org.matchlab.academic_net.di

import me.tatarka.inject.annotations.Scope

/**
 * Lives as long as one session of a signed-in user: what is scoped with it (the data sources, the repositories, the
 * caches, the session client of the Gateway) is created once for that user and thrown away with the
 * [SessionComponent] when the session ends, so that nothing of one account is seen by the next.
 */
@Scope
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER)
internal annotation class SessionScope
