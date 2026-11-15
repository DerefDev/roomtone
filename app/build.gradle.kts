import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// chiavi locali (non versionate): ../secrets.properties
val secrets = Properties().apply {
    rootProject.file("secrets.properties").takeIf { it.exists() }?.reader()?.use { load(it) }
}

android {
    namespace = "dev.deref.vinylkiosk"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.deref.vinylkiosk"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.3"
        buildConfigField("String", "LASTFM_API_KEY", "\"${secrets.getProperty("LASTFM_API_KEY", "").trim()}\"")
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    testImplementation(libs.junit)
}
