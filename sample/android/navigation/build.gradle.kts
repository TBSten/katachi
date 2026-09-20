plugins {
    // AGP 9 compiles Kotlin itself; see the root build file.
    alias(sampleLibs.plugins.androidLibrary)
}

android {
    namespace = "com.example.sample.navigation"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
