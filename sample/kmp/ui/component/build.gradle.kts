plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(sampleLibs.plugins.androidKotlinMultiplatformLibrary)
}

kotlin {
    jvmToolchain(21)

    androidLibrary {
        namespace = "com.example.kmp.ui.component"
        compileSdk = 36
        minSdk = 24
    }

    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api(project(":ui:theme"))
        }
    }
}
