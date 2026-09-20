plugins {
    alias(sampleLibs.plugins.androidApplication)
    // NOTE: do NOT add `org.jetbrains.kotlin.android` here. Since AGP 9.0 the Android plugin
    // brings its own Kotlin support and applying the Kotlin Android plugin fails the build
    // with "The 'org.jetbrains.kotlin.android' plugin is no longer required for Kotlin
    // support since AGP 9.0". The `kotlin { }` block below still works: AGP provides it.
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
    jvmToolchain(21)
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

    // katachi is a JVM library, so its tests have to run on a JVM source set. This sample
    // has no JVM target, so every katachi test of the sample lives here, in the unit tests
    // of the one plain Android module.
    testImplementation(libs.katachi)
    testImplementation(libs.kotestRunnerJunit5)
    testImplementation(libs.kotestAssertionsCore)
    testImplementation(project(":testing"))
}
