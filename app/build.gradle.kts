plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.sleeptile"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.sleeptile"
        minSdk = 29
        // 34, а не 35: на Android 15 приложениям с targetSdk 35 запрещён
        // запрос аудиофокуса из фона, а он нужен для остановки звука.
        targetSdk = 34
        versionCode = 2
        versionName = "2.0"
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
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
}
