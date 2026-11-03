package me.tbsten.katachi.internal

// Read from a resource, not the jar manifest: implementationVersion is null when loaded from a class directory
// (:katachi:test, composite build).
internal object KatachiVersion {
    internal const val RESOURCE: String = "META-INF/katachi/version.properties"

    internal const val UNKNOWN: String = "0.0.0-unknown"

    fun current(): String {
        val text = runCatching {
            KatachiVersion::class.java.classLoader.getResourceAsStream(RESOURCE)
                ?.use { it.readBytes().toString(Charsets.UTF_8) }
        }.getOrNull()
        return parse(text)
    }

    internal fun parse(text: String?): String {
        val line = text?.lineSequence()
            ?.map { it.trim() }
            ?.firstOrNull { it.startsWith("version=") }
            ?: return UNKNOWN
        return line.removePrefix("version=").trim().ifEmpty { UNKNOWN }
    }
}
