plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val supabaseUrl = providers.environmentVariable("SUPABASE_URL").orElse("").get()
val supabaseAnonKey = providers.environmentVariable("SUPABASE_ANON_KEY").orElse("").get()
fun quotedBuildConfig(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "com.edulock.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.edulock.app"
        minSdk = 26
        targetSdk = 35
        // Keep applicationId unchanged and increase this on every CI build.
        // Android upgrades require the installed and new APK signing certificates to match.
        val ciBuildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionCode = maxOf(1000, ciBuildNumber + 1000)
        versionName = "1.0.$ciBuildNumber"
        buildConfigField("String", "SUPABASE_URL", quotedBuildConfig(supabaseUrl))
        buildConfigField("String", "SUPABASE_ANON_KEY", quotedBuildConfig(supabaseAnonKey))
    }
    // Force the debug build to use the keystore preserved by android.yml instead
    // of allowing the Android Gradle Plugin to pick another runner-local debug key.
    signingConfigs {
        getByName("debug") {
            storeFile = file("${System.getenv("HOME")}/.android/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.mlkit:translate:17.0.3")
    implementation("androidx.media3:media3-transformer:1.5.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
