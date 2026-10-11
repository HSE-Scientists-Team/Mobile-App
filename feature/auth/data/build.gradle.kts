import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.kover)
}

// Auth feature, data layer: the session in the secure store and the repository. There is no
// backend yet: the only data source is the in-memory MockAuthDataSource.
kotlin {
    android {
        namespace = "org.matchlab.academic_net.feature.auth.data"
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
            api(project(":feature:auth:domain"))
            // SessionStorage takes a SecureStore; the composition root creates it per platform.
            api(project(":securestore"))
            implementation(project(":utils"))
            implementation(project(":mockserver"))
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
