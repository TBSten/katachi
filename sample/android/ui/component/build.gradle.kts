plugins {
    // AGP 9 compiles Kotlin itself; see the root build file.
    alias(sampleLibs.plugins.androidLibrary)
}

android {
    namespace = "com.example.sample.ui.component"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":ui:core"))
    implementation(project(":ui:theme"))
}
