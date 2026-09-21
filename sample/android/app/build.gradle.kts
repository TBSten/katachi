plugins {
    // No `org.jetbrains.kotlin.android` here: AGP 9 compiles Kotlin itself. The root
    // build file explains how the compiler version is raised to katachi's.
    alias(sampleLibs.plugins.androidApplication)
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.example.sample"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.sample"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":feature:home"))
    implementation(project(":feature:settings"))
    implementation(project(":navigation"))
    implementation(project(":ui"))

    implementation(platform(sampleLibs.composeBom))
    implementation(sampleLibs.androidxActivityCompose)
    implementation(sampleLibs.androidxCoreKtx)
    implementation(sampleLibs.composeUiToolingPreview)
    debugImplementation(sampleLibs.composeUiTooling)

    // Resolved through the composite build declared in settings.gradle.kts.
}
