import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.kover)
}

// The mock server's "database": what the mock sign-in answers from, the account every sign-in lands on and the
// registry of trusted e-mail domains. Everything else (people, projects) is the Gateway's now. Depends only on the
// shared model, has no data sources of its own and no feature knows another through it.
kotlin {
    android {
        namespace = "org.matchlab.academic_net.mockserver"
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
            // The account is made of the common model.
            api(project(":common"))
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
