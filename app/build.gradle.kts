plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.kugo.autoclicker"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.kugo.autoclicker"
        minSdk = 24
        targetSdk = 34
        versionCode = 9
        versionName = "1.1.2"
    }

    signingConfigs {
        create("release") {
            val ks = System.getenv("KUGO_KEYSTORE")
            storeFile = file(ks ?: "${System.getProperty("user.home")}/.android/debug.keystore")
            storePassword = System.getenv("KUGO_STORE_PASSWORD") ?: "android"
            keyAlias = System.getenv("KUGO_KEY_ALIAS") ?: "androiddebugkey"
            keyPassword = System.getenv("KUGO_KEY_PASSWORD") ?: "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
}
