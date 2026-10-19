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
include(":securestore")
include(":gateway")
include(":common")
include(":components")
include(":feature:projects:domain")
include(":feature:projects:data")
include(":feature:projects:ui")
include(":feature:profile:domain")
include(":feature:profile:data")
include(":feature:profile:ui")
include(":feature:chats:domain")
include(":feature:chats:data")
include(":feature:chats:ui")
include(":feature:responses:domain")
include(":feature:responses:data")
include(":feature:responses:ui")
include(":feature:complaints:domain")
include(":feature:complaints:data")
include(":feature:complaints:ui")
include(":feature:collaborators:domain")
include(":feature:collaborators:data")
include(":feature:collaborators:ui")
include(":feature:projectsearch:domain")
include(":feature:projectsearch:data")
include(":feature:projectsearch:ui")
include(":feature:projectdetails:domain")
include(":feature:projectdetails:data")
include(":feature:projectdetails:ui")
include(":feature:feed:domain")
include(":feature:feed:data")
include(":feature:feed:ui")
include(":feature:auth:domain")
include(":feature:auth:data")
include(":feature:settings:domain")
include(":feature:settings:data")
include(":feature:settings:ui")
include(":feature:auth:ui")
include(":catalog")