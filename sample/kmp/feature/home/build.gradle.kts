plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(sampleLibs.plugins.androidKotlinMultiplatformLibrary)
}

kotlin {
    jvmToolchain(21)

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
            api(project(":ui:core"))
            api(project(":navigation"))
            implementation(project(":ui:component"))
        }
    }
}
