plugins {
    alias(sampleLibs.plugins.androidApplication)
    // NOTE: do NOT add `org.jetbrains.kotlin.android` here. Since AGP 9.0 the Android plugin
    // brings its own Kotlin support and applying the Kotlin Android plugin fails the build
    // with "The 'org.jetbrains.kotlin.android' plugin is no longer required for Kotlin
    // support since AGP 9.0". The `kotlin { }` block below still works: AGP provides it.
    //
    // The Compose compiler plugin, on the other hand, is needed: it is what turns the
    // `@Composable` functions in this module into real Compose code. It attaches to the
    // Kotlin compilation AGP sets up.
    alias(libs.plugins.kotlinPluginCompose)
}

android {
    namespace = "com.example.kmp.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.kmp.app"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

// Required. AGP's unit test tasks default to JUnit 4, and without this kotest discovers
// nothing. Gradle 9 turns that into a hard failure ("the test task did not discover any
// tests to execute") rather than a silently green build.
tasks.withType<Test>().configureEach { useJUnitPlatform() }

dependencies {
    implementation(project(":data"))
    implementation(project(":feature:home"))
    implementation(project(":feature:settings"))
    implementation(project(":navigation"))
    // `AppTheme`. The Compose artifacts themselves arrive transitively: `:ui` exposes them
    // with `api`.
    implementation(project(":ui"))
    // `ComponentActivity.setContent`.
    implementation(sampleLibs.androidxActivityCompose)

    // The katachi tests are NOT here: they live in `:architecture-test`, a plain JVM module
    // of their own. What remains is this module's own unit tests -- the Compose-free parts
    // of the sample, which need a JVM source set and have none of their own.
    testImplementation(libs.kotestRunnerJunit5)
    testImplementation(libs.kotestAssertionsCore)
    testImplementation(project(":testing"))
}
