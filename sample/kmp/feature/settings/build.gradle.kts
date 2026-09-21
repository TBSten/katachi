plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(sampleLibs.plugins.androidKotlinMultiplatformLibrary)
    alias(sampleLibs.plugins.composeMultiplatform)
    alias(libs.plugins.kotlinPluginCompose)
}

kotlin {
    jvmToolchain(21)

    androidLibrary {
        namespace = "com.example.kmp.feature.settings"
        compileSdk = 36
        minSdk = 24
    }

    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api(project(":data"))
            api(project(":ui"))
            api(project(":navigation"))
            api(sampleLibs.lifecycleViewmodel)
            // `viewModel { }` inside `SettingsRoute`.
            implementation(sampleLibs.lifecycleViewmodelCompose)
            implementation(sampleLibs.kotlinxCoroutinesCore)
        }
    }
}
