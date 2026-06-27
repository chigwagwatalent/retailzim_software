import java.util.Properties
import java.io.FileInputStream

val keyProps = Properties()
val keyPropsFile = rootProject.file("key.properties")
if (keyPropsFile.exists()) keyProps.load(FileInputStream(keyPropsFile))

plugins {
    id("com.android.application")
    id("kotlin-android")
    id("dev.flutter.flutter-gradle-plugin")
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
        create("release") {
            keyAlias      = keyProps["keyAlias"]     as String
            keyPassword   = keyProps["keyPassword"]  as String
            storeFile     = file(keyProps["storeFile"] as String)
            storePassword = keyProps["storePassword"] as String
        }
    }

    defaultConfig {
        applicationId = "zw.co.retailzw.pos"
        minSdk        = flutter.minSdkVersion
        targetSdk     = flutter.targetSdkVersion
        versionCode   = flutter.versionCode
        versionName   = flutter.versionName
    }

    buildTypes {
        release {
            signingConfig     = signingConfigs.getByName("release")
            isMinifyEnabled   = false
            isShrinkResources = false
        }
    }
}

flutter {
    source = "../.."
}
