import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.kover)
}

// Collaborators feature, data layer: the repository, and the one server it talks to, the Gateway
// (GatewayCollaboratorsDataSource over the people search PeopleSearchApi).
kotlin {
    android {
        namespace = "org.matchlab.academic_net.feature.collaborators.data"
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
            api(project(":feature:collaborators:domain"))
            implementation(project(":utils"))
            // PeopleSearchApi and the translation of its answer for GatewayCollaboratorsDataSource; the feature never
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
