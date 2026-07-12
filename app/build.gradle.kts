plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.mgbalink"
    // NOTE: compileSdk/targetSdk 35 = Android 15, the current safe baseline as of mid-2026.
    // If Android Studio nags you to bump to 36, that's fine to do — just bump both numbers below.
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.mgbalink"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"

        // Start with just arm64 while you're bringing the build up — it's what every phone from
        // the last ~7 years actually uses. Add more ABIs back once this one compiles cleanly.
        ndk {
            abiFilters += listOf("arm64-v8a")
            version = "27.2.12479018"
        }

        externalNativeBuild {
            cmake {
                arguments += listOf("-DANDROID_STL=c++_shared")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        viewBinding = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.0")
}
