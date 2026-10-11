import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kover)
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }
    
    android {
       namespace = "org.matchlab.academic_net.shared"
       compileSdk = libs.versions.android.compileSdk.get().toInt()
       minSdk = libs.versions.android.minSdk.get().toInt()
    
       compilerOptions {
           jvmTarget = JvmTarget.JVM_11
       }
       androidResources {
           enable = true
       }
       withHostTest {
           isIncludeAndroidResources = true
       }
       withDeviceTestBuilder {
           sourceSetTreeName = "test"
       }.configure {
           instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
       }
    }
    
    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.compose.uiTooling)
        }
        commonMain.dependencies {
            implementation(project(":theme"))
            implementation(project(":components"))
            implementation(project(":feature:projects:ui"))
            implementation(project(":feature:projects:domain"))
            implementation(project(":feature:projects:data"))
            implementation(project(":feature:chats:ui"))
            implementation(project(":feature:chats:domain"))
            implementation(project(":feature:chats:data"))
            implementation(project(":feature:collaborators:ui"))
            implementation(project(":feature:collaborators:domain"))
            implementation(project(":feature:collaborators:data"))
            implementation(project(":feature:projectsearch:domain"))
            implementation(project(":feature:projectsearch:data"))
            implementation(project(":feature:projectsearch:ui"))
            implementation(project(":feature:projectdetails:domain"))
            implementation(project(":feature:projectdetails:data"))
            implementation(project(":feature:projectdetails:ui"))
            implementation(project(":feature:profile:domain"))
            implementation(project(":feature:profile:data"))
            implementation(project(":feature:profile:ui"))
            implementation(project(":feature:auth:domain"))
            implementation(project(":feature:auth:data"))
            implementation(project(":feature:auth:ui"))
            implementation(project(":feature:settings:domain"))
            implementation(project(":feature:settings:data"))
            implementation(project(":feature:settings:ui"))
            // The platform entry points hand the secure store (Keystore / Keychain) to App().
            api(project(":securestore"))
            implementation(project(":feature:feed:ui"))
            implementation(project(":feature:feed:domain"))
            implementation(project(":feature:feed:data"))
            implementation(libs.kotlin.inject.runtime)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.androidx.navigation.compose)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)

    // kotlin-inject generates the component per target; KSP lives only here, the features know nothing of it.
    add("kspAndroid", libs.kotlin.inject.compiler)
    add("kspIosArm64", libs.kotlin.inject.compiler)
    add("kspIosSimulatorArm64", libs.kotlin.inject.compiler)
}