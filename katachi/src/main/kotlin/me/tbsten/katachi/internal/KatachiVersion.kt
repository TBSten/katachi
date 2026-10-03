package me.tbsten.katachi.internal

/**
 * The version of the katachi that is running, read from a resource the build writes.
 *
 * Read from `META-INF/katachi/version.properties` rather than from the jar manifest:
 * `Package.implementationVersion` is only filled in when the class was loaded from a jar, and
 * `:katachi:test` and a composite build both load katachi from a class directory, where it is
 * `null`. A resource reads the same either way.
 */
internal object KatachiVersion {
    /** Where the build writes the version, below the root of the class path. */
    internal const val RESOURCE: String = "META-INF/katachi/version.properties"

    /** What [current] answers when the resource is missing or does not name a version. */
    internal const val UNKNOWN: String = "0.0.0-unknown"

    /** The version, or [UNKNOWN] when the resource cannot be read. Never throws. */
    fun current(): String {
        val text = runCatching {
            KatachiVersion::class.java.classLoader.getResourceAsStream(RESOURCE)
                ?.use { it.readBytes().toString(Charsets.UTF_8) }
        }.getOrNull()
        return parse(text)
    }

    /**
     * The value of the `version=` line of [text], or [UNKNOWN] when there is no such line,
     * when its value is blank, or when [text] is `null`.
     */
    internal fun parse(text: String?): String {
        val line = text?.lineSequence()
            ?.map { it.trim() }
            ?.firstOrNull { it.startsWith("version=") }
            ?: return UNKNOWN
        return line.removePrefix("version=").trim().ifEmpty { UNKNOWN }
    }
}
