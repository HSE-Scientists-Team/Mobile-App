import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.kover)
}

// Usage analytics, the platform-independent part: the contract the platform's SDK implements (AppMetrica on Android, in
// :androidApp; a Swift class on iOS), the catalog of the events, the tracker of clicks and the device's choice "send
// usage statistics". No analytics SDK is here, so shared code and tests need no application key.
kotlin {
    android {
        namespace = "org.matchlab.academic_net.analytics"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
        withHostTest {}
    }

    // Same targets as :shared, which uses the module from commonMain.
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            // The preference publishes its value as a StateFlow and runs the store calls on a dispatcher.
            api(libs.kotlinx.coroutines.core)
            // The choice is kept in the secure store; its type is part of the public constructor of the preference.
            api(project(":securestore"))
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
