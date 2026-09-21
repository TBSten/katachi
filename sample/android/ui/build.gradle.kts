plugins {
    // AGP 9 compiles Kotlin itself; see the root build file.
    alias(sampleLibs.plugins.androidLibrary)
    // Compose compiler. Applied per module rather than to every module, because only the
    // modules that actually hold `@Composable` code need it. The version comes from the
    // root build file, where it is pinned to the Kotlin version.
    id("org.jetbrains.kotlin.plugin.compose")
}

// One module for the whole UI layer. `component` / `theme` / `core` are packages
// inside it, not separate Gradle modules: katachi has to be able to say "this role
// lives in this package of this module", which is the common shape in real projects.
android {
    namespace = "com.example.sample.ui"
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
    // `api`, not `implementation`: the public composables here take `Modifier` and are
    // called from `:feature:*`, so Compose has to be on their compile classpath too.
    api(platform(sampleLibs.composeBom))
    api(sampleLibs.composeUi)
    api(sampleLibs.composeMaterial3)
    implementation(sampleLibs.composeUiToolingPreview)
    // The preview renderer. Debug only: it must never ship in a release build.
    debugImplementation(sampleLibs.composeUiTooling)
}
