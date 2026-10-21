plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.ksp) apply false
    // The root project merges the coverage of every module: `./gradlew :koverHtmlReport`.
    alias(libs.plugins.kover)
}

dependencies {
    kover(project(":shared"))
    kover(project(":theme"))
    kover(project(":utils"))
    kover(project(":securestore"))
    kover(project(":analytics"))
    kover(project(":gateway"))
    kover(project(":common"))
    kover(project(":components"))
    kover(project(":catalog"))
    kover(project(":feature:projects:domain"))
    kover(project(":feature:projects:data"))
    kover(project(":feature:projects:ui"))
    kover(project(":feature:profile:domain"))
    kover(project(":feature:profile:data"))
    kover(project(":feature:profile:ui"))
    kover(project(":feature:chats:domain"))
    kover(project(":feature:chats:data"))
    kover(project(":feature:chats:ui"))
    kover(project(":feature:responses:domain"))
    kover(project(":feature:responses:data"))
    kover(project(":feature:responses:ui"))
    kover(project(":feature:complaints:domain"))
    kover(project(":feature:complaints:data"))
    kover(project(":feature:complaints:ui"))
    kover(project(":feature:collaborators:domain"))
    kover(project(":feature:collaborators:data"))
    kover(project(":feature:collaborators:ui"))
    kover(project(":feature:projectsearch:domain"))
    kover(project(":feature:projectsearch:data"))
    kover(project(":feature:projectsearch:ui"))
    kover(project(":feature:projectdetails:domain"))
    kover(project(":feature:projectdetails:data"))
    kover(project(":feature:projectdetails:ui"))
    kover(project(":feature:feed:domain"))
    kover(project(":feature:feed:data"))
    kover(project(":feature:feed:ui"))
    kover(project(":feature:auth:domain"))
    kover(project(":feature:auth:data"))
    kover(project(":feature:settings:domain"))
    kover(project(":feature:settings:data"))
    kover(project(":feature:settings:ui"))
    kover(project(":feature:auth:ui"))
}

kover {
    reports {
        filters {
            excludes {
                // Generated code has no behaviour of its own to test.
                classes(
                    "*.generated.resources.*",
                    "*ComposableSingletons*",
                    "*.BuildConfig",
                )
            }
        }
    }
}
