plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.fareza.blokku"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.fareza.blokku"
        minSdk = 24
        targetSdk = 34
        versionCode = 9
        versionName = "2.4.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val ks = System.getenv("BLOKKU_KEYSTORE") ?: "${rootDir}/release.keystore"
            if (File(ks).exists()) {
                storeFile = file(ks)
                storePassword = System.getenv("BLOKKU_STORE_PASSWORD") ?: ""
                keyAlias = System.getenv("BLOKKU_KEY_ALIAS") ?: "blokku"
                keyPassword = System.getenv("BLOKKU_KEY_PASSWORD") ?: ""
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (File(System.getenv("BLOKKU_KEYSTORE") ?: "${rootDir}/release.keystore").exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
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
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.core:core-splashscreen:1.0.1")

    // Monetization: rewarded + interstitial ads (test IDs in debug), remove-ads IAP
    implementation("com.google.android.gms:play-services-ads:23.3.0")
    implementation("com.android.billingclient:billing-ktx:7.1.1")

    testImplementation("junit:junit:4.13.2")
}
