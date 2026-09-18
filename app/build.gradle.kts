plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.sonance.musicplayer"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.sonance.musicplayer"
        minSdk = 26
        targetSdk = 36
        versionCode = 10
        versionName = "1.0.9"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val cmKeystorePath = System.getenv("CM_KEYSTORE_PATH") ?: System.getenv("CM_KEYSTORE")
            val localReleaseKeystore = file("${rootDir}/release.keystore")
            if (cmKeystorePath != null && file(cmKeystorePath).exists()) {
                storeFile = file(cmKeystorePath)
                storePassword = System.getenv("CM_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("CM_KEY_ALIAS")
                keyPassword = System.getenv("CM_KEY_PASSWORD")
            } else if (localReleaseKeystore.exists()) {
                storeFile = localReleaseKeystore
                storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "sonanceapp"
                keyAlias = System.getenv("KEY_ALIAS") ?: "releasekey"
                keyPassword = System.getenv("KEY_PASSWORD") ?: "sonanceapp"
            }
        }
        getByName("debug") {
            storeFile = file("${rootDir}/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            val cmKeystorePath = System.getenv("CM_KEYSTORE_PATH") ?: System.getenv("CM_KEYSTORE")
            val localReleaseKeystore = file("${rootDir}/release.keystore")
            if ((cmKeystorePath != null && file(cmKeystorePath).exists()) || localReleaseKeystore.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions {
        jvmTarget = "21"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media)

    debugImplementation(libs.androidx.ui.tooling)
}
