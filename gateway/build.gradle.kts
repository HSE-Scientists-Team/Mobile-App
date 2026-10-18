import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    // The only module with the serialization plugin: the DTOs of the Gateway API live here.
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.kover)
}

// Client of the MatchLab Gateway API (docs/openapi.yaml): one Ktor engine per Gateway, the DTOs of the
// API, the calls grouped by who uses them, and the translation of every failure into RemoteFailure.
// Infrastructure like :utils and :securestore: features reach it only through their data layer.
kotlin {
    android {
        namespace = "org.matchlab.academic_net.gateway"
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
            // RemoteFailure is part of what every call throws, so the consumers see it.
            api(project(":common"))
            // HttpClientEngine is part of the public API: Gateway takes one, platformHttpEngine returns one.
            api(libs.ktor.client.core)
            implementation(project(":utils"))
            implementation(libs.ktor.client.logging)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
    }
}
