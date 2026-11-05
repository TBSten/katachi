package me.tbsten.katachi.script.internal

internal object ScriptVersion {
    internal const val RESOURCE: String = "META-INF/katachi-script/version.properties"

    internal const val UNKNOWN: String = "0.0.0-unknown"

    fun current(): String {
        val text = runCatching {
            ScriptVersion::class.java.classLoader.getResourceAsStream(RESOURCE)
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
