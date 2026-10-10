rootProject.name = "AcademicNet"

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

include(":androidApp")
include(":shared")
include(":theme")
include(":utils")
include(":common")
include(":components")
include(":feature:projects:domain")
include(":feature:projects:data")
include(":feature:projects:ui")
include(":feature:profile:ui")
include(":feature:chats:domain")
include(":feature:chats:data")
include(":feature:chats:ui")
include(":feature:responses:ui")
include(":feature:collaborators:domain")
include(":feature:collaborators:data")
include(":feature:collaborators:ui")
include(":catalog")