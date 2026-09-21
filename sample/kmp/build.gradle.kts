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

    // Compose Multiplatform: the runtime artifacts and the `compose.*` accessors.
    alias(sampleLibs.plugins.composeMultiplatform) apply false
    // The Compose compiler. Released with Kotlin, so its alias lives in the root catalog and
    // carries the same version as the other `libs.plugins.kotlin*` aliases.
    alias(libs.plugins.kotlinPluginCompose) apply false
}
