plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace   = "com.mdm.client"
    compileSdk  = 34

    defaultConfig {
        applicationId   = "com.mdm.client"
        minSdk          = 26
        targetSdk       = 34
        versionCode     = 1
        versionName     = "1.0.0"

        // ── Configuración de servidor ─────────────────────────────────────────
        buildConfigField("String", "SERVER_URL", "\"http://192.168.123.155:5000\"")
        buildConfigField("String", "ADMIN_KEY", "\"\"")
        buildConfigField("long", "POLL_INTERVAL_MS", "30000L")
        buildConfigField("long", "HEARTBEAT_INTERVAL_MS", "60000L")
        buildConfigField("int", "MAX_RETRY_ATTEMPTS", "3")
        buildConfigField("long", "RETRY_DELAY_MS", "5000L")
        buildConfigField("int", "CONNECT_TIMEOUT_SECONDS", "10")
        buildConfigField("int", "READ_TIMEOUT_SECONDS", "20")
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    buildTypes {
        debug {
            isDebuggable = true
            //applicationIdSuffix = ".debug"
            buildConfigField("String", "ADMIN_KEY", "\"DEV-ADMIN-KEY-SOLO-PARA-DESARROLLO-NO-USAR-EN-PROD\"")
        }
        release {
            isMinifyEnabled   = true
            isShrinkResources = true
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

    packaging {
        resources { 
            excludes += "/META-INF/{AL2.0,LGPL2.1}" 
        }
    }
}

dependencies {
    // ── Kotlin Stdlib (CRÍTICO - debe ir primero) ───────────────────────────
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.22")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.22")
    
    // ── AndroidX ────────────────────────────────────────────────────────────
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.lifecycle:lifecycle-service:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // HTTP
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // JSON
    implementation("com.google.code.gson:gson:2.10.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // Seguridad: cifrado de prefs
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("com.google.android.gms:play-services-location:21.1.0")
}

// ── Resolución de dependencias ──────────────────────────────────────────────
configurations.all {
    resolutionStrategy {
        force("org.jetbrains.kotlin:kotlin-stdlib:1.9.22")
        force("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.22")
    }
}