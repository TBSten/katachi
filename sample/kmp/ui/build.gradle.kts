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
    jvmToolchain(17)

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
            // Named directly from the catalog: Compose Multiplatform 1.10 deprecated the
            // plugin's `compose.runtime` / `compose.ui` / ... accessors in favour of this.
            api(sampleLibs.composeRuntime)
            api(sampleLibs.composeFoundation)
            api(sampleLibs.composeMaterial3)
            api(sampleLibs.composeUi)
            // The `@Preview` annotation, which is NOT part of `ui`.
            //
            // `org.jetbrains.compose.ui:ui-tooling-preview` is a multiplatform artifact, so
            // `@Preview` can be written in commonMain. Since Compose Multiplatform 1.10 the
            // annotation it carries is spelled `androidx.compose.ui.tooling.preview.Preview` —
            // the same fully qualified name as the Android-only annotation, but a different
            // artifact. Do not reach for `androidx.compose.ui:ui-tooling-preview`, which has no
            // iOS variant.
            //
            // The older `org.jetbrains.compose.components:components-ui-tooling-preview`
            // (`org.jetbrains.compose.ui.tooling.preview.Preview`) still resolves but the
            // compiler reports its annotation as deprecated.
            //
            // `api` because the previews live in the feature modules, not here.
            api(sampleLibs.composeUiToolingPreview)
        }
    }
}
