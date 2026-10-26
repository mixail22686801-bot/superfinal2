plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "ru.schedule"
    compileSdk = 34
    signingConfigs {
        getByName("debug") {
            val ks = file("debug.p12")
            if (ks.exists()) {
                storeFile = ks
                storePassword = "android"
                keyAlias = "key"
                keyPassword = "android"
                storeType = "pkcs12"
            }
        }
    }
    defaultConfig {
        applicationId = "ru.schedule"
        minSdk = 24
        targetSdk = 34
        versionCode = 3
        versionName = "1.2"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
