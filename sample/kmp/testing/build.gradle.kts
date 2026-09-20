plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(sampleLibs.plugins.androidKotlinMultiplatformLibrary)
}

kotlin {
    jvmToolchain(21)

    androidLibrary {
        namespace = "com.example.kmp.testing"
        compileSdk = 36
        minSdk = 24
    }

    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            // `api`, not `implementation`: the fakes implement `:data` interfaces, and the
            // modules that consume them need those interfaces on their compile classpath.
            api(project(":data"))
        }
    }
}
