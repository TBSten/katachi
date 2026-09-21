plugins {
    alias(sampleLibs.plugins.androidApplication) apply false
    alias(sampleLibs.plugins.androidKotlinMultiplatformLibrary) apply false

    // Declared here (and applied in the modules that need it) for two reasons.
    //
    // 1. It is the plugin the Kotlin Multiplatform modules apply.
    // 2. It pins the Kotlin Gradle Plugin on the buildscript classpath to the version of the
    //    root catalog (2.4.10). AGP 9.1.0 bundles KGP 2.2.10 and uses it for its built-in
    //    Kotlin support, so without this line `:app:android` would compile against 2.2.10
    //    and fail to read katachi's metadata:
    //    "Module was compiled with an incompatible version of Kotlin".
    //    Keep this declaration even though the root project applies no plugin itself.
    //
    // If a future AGP stops honouring the classpath version, the fallbacks are, in order:
    // compile the sample with `-Xskip-metadata-version-check`, hold AGP back, or build
    // katachi with the older Kotlin.
    alias(libs.plugins.kotlinMultiplatform) apply false

    // `:architecture-test` is a plain JVM module. The declaration has to be here rather than
    // only in that module: the Kotlin Gradle Plugin is already on the buildscript classpath
    // (the line above puts it there), and asking for `org.jetbrains.kotlin.jvm` with a
    // version in a subproject then fails with "the plugin is already on the classpath with
    // an unknown version, so compatibility cannot be checked". Declaring both ids in the
    // same block resolves them together, from the one version of the root catalog.
    alias(libs.plugins.kotlinJvm) apply false

    // Compose Multiplatform: the runtime artifacts and the `compose.*` accessors.
    alias(sampleLibs.plugins.composeMultiplatform) apply false
    // The Compose compiler. Released with Kotlin, so its alias lives in the root catalog and
    // carries the same version as the other `libs.plugins.kotlin*` aliases.
    alias(libs.plugins.kotlinPluginCompose) apply false
}
