plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(sampleLibs.plugins.androidKotlinMultiplatformLibrary)
}

kotlin {
    jvmToolchain(21)

    androidLibrary {
        namespace = "com.example.kmp.navigation"
        compileSdk = 36
        minSdk = 24
    }

    iosArm64()
    iosSimulatorArm64()
}
