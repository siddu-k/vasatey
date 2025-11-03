plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.gms.google.services)
}

android {
    namespace = "com.sriox.vasatey"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.sriox.vasatey"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    androidResources {
        // Prevent compression of Picovoice model files
        noCompress += listOf("pv", "ppn")
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
    // --- Android Core ---
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)

    // --- Firebase (Only Messaging for push notifications) ---
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation("com.google.firebase:firebase-messaging-ktx")

    // --- ✅ Supabase SDK ---
    implementation("io.github.jan-tennert.supabase:postgrest-kt:2.5.2")
    implementation("io.github.jan-tennert.supabase:storage-kt:2.5.2")
    implementation("io.github.jan-tennert.supabase:realtime-kt:2.5.2")
    implementation("io.github.jan-tennert.supabase:gotrue-kt:2.5.2")

    // --- Ktor & Serialization ---
    implementation("io.ktor:ktor-client-android:2.3.5")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")

    // --- Picovoice (Wake Word Engine) ---
    implementation("ai.picovoice:porcupine-android:3.0.2")

    // --- Lifecycle + Coroutines ---
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // --- Networking ---
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)

    // --- Location & Maps ---
    implementation(libs.play.services.location)
    implementation(libs.play.services.maps)
}
