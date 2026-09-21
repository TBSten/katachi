plugins {
    alias(sampleLibs.plugins.androidApplication) apply false
    alias(sampleLibs.plugins.androidLibrary) apply false

    // WORKAROUND — do not delete without reading this.
    //
    // AGP 9 compiles Kotlin itself (`android.builtInKotlin` defaults to true) and the
    // `org.jetbrains.kotlin.android` plugin must NOT be applied to a module any more.
    // That is why this is `apply false` and why no module below applies it.
    //
    // What this line is for is the *version* of the compiler AGP runs. AGP 9.1.0 pulls
    // `org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.10` onto the buildscript classpath,
    // whose compiler reads Kotlin metadata up to 2.3.0. katachi is built with Kotlin
    // 2.4.10 (metadata 2.4.0), so without this line `:app:compileDebugUnitTestKotlin`
    // fails with:
    //   Module was compiled with an incompatible version of Kotlin.
    //   The binary version of its metadata is 2.4.0, expected version is 2.2.0
    //   Unresolved reference 'architecture'
    // Declaring the plugin here (from the root catalog, the same 2.4.10 katachi uses)
    // raises the classpath entry. Confirm with `./gradlew buildEnvironment`, which then
    // prints `org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.10 -> 2.4.10`.
    //
    // This is not a documented AGP feature, so it may stop working. Fallbacks, in the
    // order we would try them:
    //   1. add `-Xskip-metadata-version-check` to the Android modules' Kotlin options
    //   2. hold AGP at 8.13.x, where KGP is applied explicitly and the version is ours
    //   3. build katachi against the Kotlin version AGP bundles
    alias(libs.plugins.kotlinAndroid) apply false

    // The Compose compiler plugin ships with Kotlin and must be kept on exactly the
    // Kotlin version above. The root catalog declares it against the same `kotlin`
    // version reference, so it follows `libs` — the SSoT — without the version being
    // duplicated into `sample.versions.toml` or spelled out here.
    alias(libs.plugins.kotlinPluginCompose) apply false
}
