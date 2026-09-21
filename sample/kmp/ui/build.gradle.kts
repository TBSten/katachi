plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(sampleLibs.plugins.androidKotlinMultiplatformLibrary)
    alias(sampleLibs.plugins.composeMultiplatform)
    alias(libs.plugins.kotlinPluginCompose)
}

// One module for the whole UI layer. `component` / `theme` / `core` are packages
// inside it, not separate Gradle modules: katachi has to be able to say "this role
// lives in this package of this module", which is the common shape in real projects.
kotlin {
    jvmToolchain(21)

    androidLibrary {
        namespace = "com.example.kmp.ui"
        compileSdk = 36
        minSdk = 24
    }

    // Declared but never compiled by CI: see app/ios/README.md.
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            // `api`, not `implementation`: `AppTheme` and `PrimaryButton` are themselves
            // `@Composable`, so every caller needs Compose on its own compile classpath.
            api(compose.runtime)
            api(compose.foundation)
            api(compose.material3)
            api(compose.ui)
            // The `@Preview` annotation, which is NOT part of `compose.ui`.
            //
            // `compose.preview` is `org.jetbrains.compose.ui:ui-tooling-preview`: a
            // multiplatform artifact, so `@Preview` can be written in commonMain. Since
            // Compose Multiplatform 1.10 the annotation it carries is spelled
            // `androidx.compose.ui.tooling.preview.Preview` — the same fully qualified name
            // as the Android-only annotation, but a different artifact. Do not reach for
            // `androidx.compose.ui:ui-tooling-preview`, which has no iOS variant.
            //
            // The older `compose.components.uiToolingPreview`
            // (`org.jetbrains.compose.ui.tooling.preview.Preview`) still resolves but the
            // compiler reports its annotation as deprecated.
            //
            // `api` because the previews live in the feature modules, not here.
            api(compose.preview)
        }
    }
}
