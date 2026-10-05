package me.tbsten.katachi.dsl.internal

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.MetadataKey
import me.tbsten.katachi.dsl.metadata

/**
 * The roles `gradle()` declares, one per kind of Gradle file: what a hook of
 * [me.tbsten.katachi.dsl.GradleScope] names, and the key the hooks are recorded under.
 */
internal enum class GradleRoleKind(val roleName: String) {
    LauncherScript("LauncherScript"),
    WrapperJar("WrapperJar"),
    WrapperProperties("WrapperProperties"),
    SettingsScript("SettingsScript"),
    BuildScript("BuildScript"),
    GradleProperties("GradleProperties"),
    VersionCatalog("VersionCatalog"),
    DaemonJvmProperties("DaemonJvmProperties"),
}

/**
 * `true` on every role `gradle()` declared, so that a reader can tell katachi's own Gradle
 * roles from one a user wrote -- `Gradle.BuildScript` declares `build.gradle.kts` in every
 * module on its own account (not as a synthetic line of the module sugar, so that a constraint
 * written in `buildScript { }` covers it), yet nobody wrote "put a file here", and the Nearby
 * locations of an `[UnexpectedFile]` leave it out for that reason.
 *
 * Internal: a marker for katachi's own readers, not a word of the vocabulary a definition writes.
 */
@OptIn(ExperimentalKatachiApi::class)
internal val DeclaredByGradle: MetadataKey<Boolean> = metadata(Boolean::class)
