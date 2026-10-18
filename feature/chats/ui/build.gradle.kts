import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kover)
}

kotlin {
    android {
        namespace = "org.matchlab.academic_net.feature.chats.ui"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
        androidResources {
            enable = true
        }
        withHostTest {}
    }

    // Same targets as :shared, which will use the feature from commonMain.
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            // UI models expose component types (e.g. DrawableResource, chip tones).
            api(project(":components"))
            // The screens' entry points take the feature's use cases.
            api(project(":feature:chats:domain"))
            implementation(project(":utils"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            // View models are tested over the real repository and a fake data source with a history (see TestSetup.kt).
            implementation(project(":feature:chats:data"))
        }
    }
}

// Every feature UI module is named "ui", so the generated Res package has to be set explicitly.
compose.resources {
    packageOfResClass = "org.matchlab.academic_net.feature.chats.ui.generated.resources"
}
