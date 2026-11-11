package me.tbsten.katachi.intellij.data.detect

/** A library a synced module depends on, as the IDE's sync data describes it. */
internal data class SyncedLibrary(
    val groupId: String?,
    val artifactId: String?,
    val version: String?,
    /** `me.tbsten.katachi:katachi:0.4.0`, `Gradle: me.tbsten.katachi:katachi:0.4.0`: the resolved coordinates, whatever the build script wrote. */
    val names: List<String>,
)

/**
 * The version of katachi when [library] is katachi itself, or `null`. Android Studio's sync leaves
 * the coordinates empty and keeps only the name, so the name is read too (KATACHI-363).
 */
internal fun katachiVersionOf(library: SyncedLibrary): String? {
    val fromName = library.names.firstNotNullOfOrNull(::katachiVersionInName)
    return when {
        library.groupId == KATACHI_GROUP && library.artifactId == KATACHI_ARTIFACT -> library.version ?: fromName
        else -> fromName
    }
}

private fun katachiVersionInName(name: String): String? {
    // `Gradle: ` (or another prefix) ends with ": "; the coordinates have no space.
    val parts = name.substringAfterLast(": ").split(':')
    if (parts.size < 3 || parts[0] != KATACHI_GROUP || parts[1] != KATACHI_ARTIFACT) return null
    return parts[2].substringBefore('@').ifEmpty { null }
}

private const val KATACHI_GROUP = "me.tbsten.katachi"
private const val KATACHI_ARTIFACT = "katachi"
