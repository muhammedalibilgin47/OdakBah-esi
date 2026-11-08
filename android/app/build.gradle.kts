plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.gms.google-services")
}

// Sabit imza anahtarı (Google girişi için). Şifre GitHub gizli ayarından gelir.
val odakKeystore = rootProject.file("keystore/odak-release.jks")
val odakKeystorePassword: String? = System.getenv("ODAK_KEYSTORE_PASSWORD")
val hasOdakKey = odakKeystore.exists() && !odakKeystorePassword.isNullOrEmpty()

android {
    namespace = "com.tiyzi.odakbahcesi"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tiyzi.odakbahcesi"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "1.4"
    }

    signingConfigs {
        create("odak") {
            if (hasOdakKey) {
                storeFile = odakKeystore
                storePassword = odakKeystorePassword
                keyAlias = "odak"
                keyPassword = odakKeystorePassword
            }
        }
    }

    buildTypes {
        debug {
            if (hasOdakKey) signingConfig = signingConfigs.getByName("odak")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    // Veritabanı (Room)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Firebase: hesap + bulut
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
    // Google ile giriş (Credential Manager)
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
