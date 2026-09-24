plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(sampleLibs.plugins.androidKotlinMultiplatformLibrary)
}

kotlin {
    jvmToolchain(17)

    androidLibrary {
        namespace = "com.example.kmp.data"
        compileSdk = 36
        minSdk = 24
    }

    // Declared but never compiled by CI: see app/ios/README.md.
    iosArm64()
    iosSimulatorArm64()
}
