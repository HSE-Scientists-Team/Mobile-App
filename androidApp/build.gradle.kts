import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

// The MatchLab Gateway the app talks to, chosen at build time (see Backend in :shared):
//   -Pmatchlab.gatewayUrl=<url>       empty (default) is the production server.
//                                     A local Docker Compose is http://10.0.2.2:8080 from an emulator (cleartext is
//                                     allowed to 10.0.2.2 and localhost in debug builds only)
val matchlabGatewayUrl: String = providers.gradleProperty("matchlab.gatewayUrl").orElse("").get().trim()
require(
    matchlabGatewayUrl.isEmpty() ||
        matchlabGatewayUrl.startsWith("https://", ignoreCase = true) ||
        matchlabGatewayUrl.startsWith("http://", ignoreCase = true),
) {
    "matchlab.gatewayUrl must start with http:// or https://, not '$matchlabGatewayUrl'"
}

/** [value] as the Java string literal of a BuildConfig field. */
fun javaString(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
dependencies {
    implementation(project(":shared"))
    implementation(project(":securestore"))
    // Component catalog screen (src/debug).
    debugImplementation(project(":catalog"))

    implementation(libs.androidx.activity.compose)

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)
}

android {
    namespace = "org.matchlab.academic_net"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "org.matchlab.academic_net"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "GATEWAY_URL", javaString(matchlabGatewayUrl))
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}