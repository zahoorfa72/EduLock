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
        versionCode = 2
        versionName = "0.2.0"
        buildConfigField("String", "SUPABASE_URL", quotedBuildConfig(supabaseUrl))
        buildConfigField("String", "SUPABASE_ANON_KEY", quotedBuildConfig(supabaseAnonKey))
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
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
