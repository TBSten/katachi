plugins {
    // AGP 9 compiles Kotlin itself; see the root build file.
    alias(sampleLibs.plugins.androidLibrary)
    alias(libs.plugins.kotlinPluginCompose)
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

kotlin {
    // Every compiler warning fails the build, so none accumulate unnoticed.
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
}
