plugins {
    // AGP 9 compiles Kotlin itself; see the root build file.
    alias(sampleLibs.plugins.androidLibrary)
}

android {
    namespace = "com.example.sample.testing"
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
    // `api`, not `implementation`: the fakes implement `:data` interfaces, and the
    // modules that consume them need those interfaces on their compile classpath.
    api(project(":data"))
}
