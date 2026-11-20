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
    namespace = "dev.deref.roomtone"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.deref.roomtone"
        minSdk = 26
        targetSdk = 36
        versionCode = 11
        versionName = "0.8"
        buildConfigField("String", "LASTFM_API_KEY", "\"${secrets.getProperty("LASTFM_API_KEY", "").trim()}\"")
        buildConfigField("String", "LASTFM_API_SECRET", "\"${secrets.getProperty("LASTFM_API_SECRET", "").trim()}\"")
        buildConfigField("String", "DISCOGS_CONSUMER_KEY", "\"${secrets.getProperty("DISCOGS_CONSUMER_KEY", "").trim()}\"")
        buildConfigField("String", "DISCOGS_CONSUMER_SECRET", "\"${secrets.getProperty("DISCOGS_CONSUMER_SECRET", "").trim()}\"")
        buildConfigField("String", "GENIUS_ACCESS_TOKEN", "\"${secrets.getProperty("GENIUS_ACCESS_TOKEN", "").trim()}\"")
    }
    androidResources {
        generateLocaleConfig = true // locales_config dalle cartelle values-*
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.palette)
    implementation(libs.zxing.core)
    testImplementation(libs.junit)
}
