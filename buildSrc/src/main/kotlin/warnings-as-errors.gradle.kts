package buildsrc.convention

// Turns every Kotlin compiler warning into an error. Applied explicitly from each module's
// build.gradle.kts (not folded into `kotlin-jvm`) so the setting stays visible there.
plugins {
    kotlin("jvm")
}

kotlin {
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
}
