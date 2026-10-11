package org.matchlab.academic_net.di

import me.tatarka.inject.annotations.Scope

/**
 * Lives as long as the process: what is scoped with it (data sources, repositories) is created
 * once, so the mock data survives a configuration change like it did with the manual container.
 */
@Scope
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER)
internal annotation class AppScope
