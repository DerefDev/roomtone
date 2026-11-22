import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// chiavi locali (non versionate): ../secrets.properties
val secrets = Properties().apply {
    rootProject.file("secrets.properties").takeIf { it.exists() }?.reader()?.use { load(it) }
}

// lingue = inglese + ogni cartella values-xx tradotta almeno all'80% ("pt-rBR" → "pt-BR", "b+sr+Latn" → "sr-Latn").
// Valgono per il menu dell'app (BuildConfig.LANGS) e per l'elenco di sistema (locales_config); le lingue sotto soglia
// restano nell'APK (stringhe tradotte + inglese) ma non compaiono. Crowdin esporta anche file vuoti per le lingue non tradotte.
fun count(f: File) = Regex("<(string|plurals) name=\"[^\"]+\"(?! translatable=\"false\")").findAll(f.readText()).count()
val total = count(file("src/main/res/values/strings.xml"))
val appLangs = listOf("en") + (file("src/main/res").listFiles() ?: emptyArray())
    .filter { it.name.startsWith("values-") && File(it, "strings.xml").let { f -> f.exists() && count(f) * 5 >= total * 4 } }
    .map { it.name.removePrefix("values-").removePrefix("b+").replace("-r", "-").replace('+', '-') }.sorted()

abstract class LocaleConfigTask : DefaultTask() {
    @get:Input abstract val langs: ListProperty<String>
    @get:OutputDirectory abstract val outDir: DirectoryProperty
    @TaskAction fun write() {
        val f = outDir.file("xml/locales_config.xml").get().asFile
        f.parentFile.mkdirs()
        f.writeText(langs.get().joinToString("\n", "<locale-config xmlns:android=\"http://schemas.android.com/apk/res/android\">\n", "\n</locale-config>\n") {
            "    <locale android:name=\"$it\"/>"
        })
    }
}
val localeConfig = tasks.register<LocaleConfigTask>("localeConfig") { langs.set(appLangs) }

android {
    namespace = "dev.deref.roomtone"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.deref.roomtone"
        minSdk = 26
        targetSdk = 36
        versionCode = 13
        // in CI the name comes from the tag (v0.9-beta1 → "0.9-beta1"); locally from here
        versionName = System.getenv("GITHUB_REF_NAME")?.takeIf { it.startsWith("v") }?.removePrefix("v") ?: "0.9"
        buildConfigField("String", "LASTFM_API_KEY", "\"${secrets.getProperty("LASTFM_API_KEY", "").trim()}\"")
        buildConfigField("String", "LASTFM_API_SECRET", "\"${secrets.getProperty("LASTFM_API_SECRET", "").trim()}\"")
        buildConfigField("String", "DISCOGS_CONSUMER_KEY", "\"${secrets.getProperty("DISCOGS_CONSUMER_KEY", "").trim()}\"")
        buildConfigField("String", "DISCOGS_CONSUMER_SECRET", "\"${secrets.getProperty("DISCOGS_CONSUMER_SECRET", "").trim()}\"")
        buildConfigField("String", "LANGS", "\"${appLangs.joinToString(",")}\"")
        buildConfigField("String", "GENIUS_ACCESS_TOKEN", "\"${secrets.getProperty("GENIUS_ACCESS_TOKEN", "").trim()}\"")
    }
    // firma release: keystore da env (CI) o secrets.properties (locale), stessa password per store e chiave (PKCS12).
    // ponytail: senza keystore la release si firma con la chiave debug, solo per provare R8 in locale; il workflow controlla che ci sia.
    val ks = (System.getenv("KEYSTORE_FILE") ?: secrets.getProperty("KEYSTORE_FILE"))?.let { file(it) }?.takeIf { it.exists() }
    val ksPass = System.getenv("KEYSTORE_PASSWORD") ?: secrets.getProperty("KEYSTORE_PASSWORD")
    signingConfigs {
        if (ks != null) create("release") { storeFile = ks; storePassword = ksPass; keyAlias = "roomtone"; keyPassword = ksPass }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

androidComponents {
    onVariants { it.sources.res?.addGeneratedSourceDirectory(localeConfig, LocaleConfigTask::outDir) }
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
