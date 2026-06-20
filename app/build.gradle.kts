plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.huawei.agconnect")
}

android {
    namespace = "com.dev2026.dooropener"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.dev2026.dooropener"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
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
        viewBinding = true
    }
}

dependencies {
    // AndroidX Core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-service:2.7.0")
    implementation("androidx.preference:preference-ktx:1.2.1")

    // Material Design
    implementation("com.google.android.material:material:1.11.0")

    // AppGallery Connect base SDK (required by agconnect-services.json)
    implementation("com.huawei.agconnect:agconnect-core:1.5.2.300")

    // Huawei Wear Engine SDK
    implementation("com.huawei.hms:wearengine:6.11.0.302")

    // Huawei Health Kit (optional — if we need health app bridge)
    // implementation("com.huawei.hms:health:6.12.0.300")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // MQTT Client (for 联掌门户 integration — Eclipse Paho)
    implementation("org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5")
    implementation("org.eclipse.paho:org.eclipse.paho.android.service:1.1.1")

    // OkHttp (for HTTP API calls to 联掌门户)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Gson (for JSON parsing)
    implementation("com.google.code.gson:gson:2.10.1")

    // Encrypted SharedPreferences for secure credential storage
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // DataStore Preferences (for non-sensitive preferences)
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("io.mockk:mockk:1.13.9")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
