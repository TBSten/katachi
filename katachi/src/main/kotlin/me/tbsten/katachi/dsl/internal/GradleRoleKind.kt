package me.tbsten.katachi.dsl.internal

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.MetadataKey
import me.tbsten.katachi.dsl.metadata

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

// Not a synthetic module line: a constraint written in `buildScript { }` must cover it; Nearby locations of
// UnexpectedFile leave it out.
@OptIn(ExperimentalKatachiApi::class)
internal val DeclaredByGradle: MetadataKey<Boolean> = metadata(Boolean::class)
