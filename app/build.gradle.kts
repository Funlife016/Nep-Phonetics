plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.nepalime.keyboard"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.nepalime.keyboard"
        // minSdk 26 covers effectively all Android tablets sold with physical
        // keyboard accessories (Samsung DeX-capable, Chromebooks-as-Android, etc).
        // Lower it if you need to target an older device.
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
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

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
}
