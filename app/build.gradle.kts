plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "co.divlab.tvlauncher"
    compileSdk = 34
    defaultConfig {
        applicationId = "co.divlab.tvlauncher"
        minSdk = 26
        targetSdk = 34
        versionCode = 19
        versionName = "0.12.1"
    }
    val ks = System.getenv("KEYSTORE_FILE")
    signingConfigs {
        if (ks != null) create("divlab") {
            storeFile = file(ks)
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = System.getenv("KEY_ALIAS") ?: "divlab"
            keyPassword = System.getenv("KEYSTORE_PASSWORD")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.findByName("divlab") ?: signingConfigs.getByName("debug")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    lint { disable += "ProtectedPermissions"; abortOnError = false }
    kotlinOptions { jvmTarget = "17" }
}
dependencies { implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.palette:palette:1.0.0") }
