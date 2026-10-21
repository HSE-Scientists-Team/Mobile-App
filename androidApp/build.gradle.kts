import java.io.StringReader
import java.util.Properties
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

// The AppMetrica application key (usage analytics), chosen at build time and never committed:
//   -Pmatchlab.appmetricaKey=<key>    or the line  matchlab.appmetricaKey=<key>  in local.properties (-P wins)
//                                     empty (default): no analytics at all (NoOpAnalytics), the build needs no key.
// Not in gradle.properties: that file is in git. The key is the UUID of Settings -> Main of the AppMetrica console.
val localProperties = Properties().apply {
    // Read through the providers, so that a change of the file invalidates the configuration cache.
    providers.fileContents(rootProject.layout.projectDirectory.file("local.properties")).asText.orNull
        ?.let { load(StringReader(it)) }
}
val matchlabAppMetricaKey: String = (
    providers.gradleProperty("matchlab.appmetricaKey").orNull
        ?: localProperties.getProperty("matchlab.appmetricaKey")
        ?: ""
    ).trim()
// UUID.fromString is lenient ("1-2-3-4-5" passes), and the SDK would then refuse the key at the start of the app.
val appMetricaKeyShape = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
require(matchlabAppMetricaKey.isEmpty() || appMetricaKeyShape.matches(matchlabAppMetricaKey)) {
    "matchlab.appmetricaKey must be the application key of the AppMetrica console (a UUID); the value given is not one"
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
    // The contract of the analytics, which AppMetricaAnalytics implements over the SDK.
    implementation(project(":analytics"))
    implementation(libs.appmetrica.analytics)
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
        versionName = "1.0.0"

        buildConfigField("String", "GATEWAY_URL", javaString(matchlabGatewayUrl))
        buildConfigField("String", "APPMETRICA_API_KEY", javaString(matchlabAppMetricaKey))
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