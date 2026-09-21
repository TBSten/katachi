plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(sampleLibs.plugins.androidKotlinMultiplatformLibrary)
}

// No Compose here on purpose: this module only holds the navigation types and the state of
// the current destination, so it stays testable without a Compose runtime.
kotlin {
    jvmToolchain(21)

    androidLibrary {
        namespace = "com.example.kmp.navigation"
        compileSdk = 36
        minSdk = 24
    }

    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            // `Navigator.current` is a `StateFlow`, so it is part of this module's surface.
            api(sampleLibs.kotlinxCoroutinesCore)
        }
    }
}
