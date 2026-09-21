plugins {
    // AGP 9 compiles Kotlin itself; see the root build file.
    alias(sampleLibs.plugins.androidLibrary)
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.example.sample.feature.home"
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
    implementation(project(":data"))
    // Both are `api`: Compose and the navigation graph builder come through them and
    // appear in this module's public signatures.
    api(project(":navigation"))
    api(project(":ui"))

    implementation(platform(sampleLibs.composeBom))
    implementation(sampleLibs.androidxLifecycleRuntimeCompose)
    implementation(sampleLibs.androidxLifecycleViewModelCompose)
    implementation(sampleLibs.composeUiToolingPreview)
    debugImplementation(sampleLibs.composeUiTooling)
}
