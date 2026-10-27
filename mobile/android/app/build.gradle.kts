import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("kotlin-android")
    id("dev.flutter.flutter-gradle-plugin")
}

val buildUnsigned = System.getenv("RETAILZW_UNSIGNED_RELEASE") == "true"
val freshInstall = System.getenv("RETAILZW_FRESH_INSTALL") == "true"
val keyProps = Properties()
val keyPropsFile = rootProject.file("key.properties")
if (!buildUnsigned && keyPropsFile.exists()) {
    FileInputStream(keyPropsFile).use { keyProps.load(it) }
}

android {
    namespace = "zw.co.retailzw.pos"
    compileSdk = 36
    ndkVersion = "27.0.12077973"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_11.toString()
    }

    signingConfigs {
        if (!buildUnsigned) create("release") {
            keyAlias      = keyProps["keyAlias"]     as String
            keyPassword   = keyProps["keyPassword"]  as String
            storeFile     = file(keyProps["storeFile"] as String)
            storePassword = keyProps["storePassword"] as String
        }
    }

    defaultConfig {
        applicationId = "zw.co.retailzw.pos"
        if (freshInstall) applicationIdSuffix = ".sunmi"
        manifestPlaceholders["appLabel"] = if (freshInstall) "RetailZW Shop" else "RetailZW POS"
        minSdk        = flutter.minSdkVersion
        targetSdk     = flutter.targetSdkVersion
        versionCode   = flutter.versionCode
        versionName   = flutter.versionName
    }

    buildTypes {
        release {
            if (!buildUnsigned) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled   = false
            isShrinkResources = false
        }
    }
}

flutter {
    source = "../.."
}

dependencies {
    implementation("com.sunmi:printerlibrary:1.0.18")
}
