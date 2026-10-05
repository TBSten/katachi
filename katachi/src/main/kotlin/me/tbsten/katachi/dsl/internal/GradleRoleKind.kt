package me.tbsten.katachi.dsl.internal

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
