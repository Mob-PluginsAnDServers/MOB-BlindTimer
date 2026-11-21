plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val runNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "0").toIntOrNull() ?: 0

android {
    namespace = "pl.mobps.blindtimer"
    compileSdk = 34

    defaultConfig {
        applicationId = "pl.mobps.blindtimer"
        minSdk = 24
        targetSdk = 34
        versionCode = 10 + runNumber
        versionName = "1.$runNumber"
    }

    signingConfigs {
        create("mob") {
            storeFile = file("mob.keystore")
            storePassword = "mobblindtimer"
            keyAlias = "mob"
            keyPassword = "mobblindtimer"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("mob")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("mob")
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
