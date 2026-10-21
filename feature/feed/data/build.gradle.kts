import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.kover)
}

// Feed feature, data layer: the repository, and the one server it talks to, the Gateway
// (GatewayFeedDataSource over the recommendations RecommendationsApi).
kotlin {
    android {
        namespace = "org.matchlab.academic_net.feature.feed.data"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
        withHostTest {}
    }

    // Same targets as :shared, which uses the feature from commonMain.
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api(project(":feature:feed:domain"))
            implementation(project(":utils"))
            // RecommendationsApi and the translation of its answer for GatewayFeedDataSource; the feature never
            // exposes them.
            implementation(project(":gateway"))
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
