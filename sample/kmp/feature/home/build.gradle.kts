plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(sampleLibs.plugins.androidKotlinMultiplatformLibrary)
    alias(sampleLibs.plugins.composeMultiplatform)
    alias(libs.plugins.kotlinPluginCompose)
}

kotlin {
    jvmToolchain(17)

    androidLibrary {
        namespace = "com.example.kmp.feature.home"
        compileSdk = 36
        minSdk = 24
    }

    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            // `api` where the type shows up in this module's own public signatures.
            api(project(":data"))
            api(project(":ui"))
            api(project(":navigation"))
            // `HomeViewModel` is a `ViewModel` and `HomeScreen` takes one, so both are part
            // of this module's surface.
            api(sampleLibs.lifecycleViewmodel)
            // `viewModel { }` inside `HomeRoute`.
            implementation(sampleLibs.lifecycleViewmodelCompose)
            implementation(sampleLibs.kotlinxCoroutinesCore)
        }
    }
}
