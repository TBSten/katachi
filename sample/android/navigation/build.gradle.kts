plugins {
    // AGP 9 compiles Kotlin itself; see the root build file.
    alias(sampleLibs.plugins.androidLibrary)
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.example.sample.navigation"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
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
    // `api`: `NavHostController` and `NavGraphBuilder` appear in this module's public
    // signatures, and `:feature:*` and `:app` write against them.
    api(platform(sampleLibs.composeBom))
    api(sampleLibs.androidxNavigationCompose)
    implementation(sampleLibs.composeRuntime)
}
