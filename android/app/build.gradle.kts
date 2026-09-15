import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Carrega keystore.properties (local, NAO versionado) se existir
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) load(keystorePropsFile.inputStream())
}
fun secret(name: String, default: String = ""): String =
    (project.findProperty(name) as String?)
        ?: System.getenv(name)
        ?: keystoreProps.getProperty(name)
        ?: default

android {
    namespace = "br.com.gruposetup.mdm"
    compileSdk = 34

    defaultConfig {
        applicationId = "br.com.gruposetup.mdm"
        minSdk = 21
        targetSdk = 34
        versionCode = 3
        versionName = "1.2.0"

        // Injetados no build (CI secret ou -P / keystore.properties). NAO ficam no repo.
        val apiBase = secret("API_BASE_URL", "https://mdm-gruposetup-backend.onrender.com")
        val apiKey = secret("API_KEY", "TROQUE_ESTA_CHAVE")
        buildConfigField("String", "API_BASE_URL", "\"$apiBase\"")
        buildConfigField("String", "API_KEY", "\"$apiKey\"")
    }

    signingConfigs {
        create("release") {
            val ksPath = secret("KEYSTORE_FILE")
            if (ksPath.isNotEmpty()) {
                storeFile = file(ksPath)
                storePassword = secret("KEYSTORE_PASSWORD")
                keyAlias = secret("KEY_ALIAS")
                keyPassword = secret("KEY_PASSWORD")
            }
        }
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            if (secret("KEYSTORE_FILE").isNotEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
