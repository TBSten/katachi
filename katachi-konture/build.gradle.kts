plugins {
    id("buildsrc.convention.kotlin-jvm")
    id("buildsrc.convention.warnings-as-errors")
    id("buildsrc.convention.katachi-kotlin-library")
    id("buildsrc.convention.katachi-publish")
}

group = "me.tbsten.katachi"
version = libs.versions.katachi.get()

kotlin {
    explicitApi()

    compilerOptions {
        optIn.add("me.tbsten.katachi.ExperimentalKatachiApi")
        // `internal/KontureParsing.kt` is the one file that reaches Konture's `internal` parser
        // (`PsiParser`, `MapSymbolLookup`) through `@file:Suppress("INVISIBLE_MEMBER",
        // "INVISIBLE_REFERENCE")`. Without this flag the compiler warns that suppressing those
        // errors is unspecified behaviour, and `allWarningsAsErrors` turns the warning into a
        // failed build. See `.local/v0.3/konture/design.md` 2.1 for the two alternatives
        // (`-Xfriend-paths`, reflection) should Kotlin ever stop honouring the suppression.
        freeCompilerArgs.add("-Xdont-warn-on-error-suppression")
    }
}

dependencies {
    api(project(":katachi"))
    api(libs.konture)

    testImplementation(testFixtures(project(":katachi")))
    testImplementation(libs.kotestRunnerJunit5)
    testImplementation(libs.kotestAssertionsCore)
}
