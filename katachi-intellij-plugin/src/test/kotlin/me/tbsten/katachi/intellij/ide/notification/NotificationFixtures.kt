package me.tbsten.katachi.intellij.ide.notification

// First: the top-level values below use it while this file initializes.
private val CAPTURE = Regex("""\$\{([A-Za-z_][A-Za-z0-9_]*)}""")

/**
 * `katachiInternalTemplatesJson` output with one file per template, [templates] being role name to
 * pattern (`${x}` a path capture), in that order: the index order of the notification tests.
 */
internal fun placementJson(vararg templates: Pair<String, String>): String {
    val summaries = templates.joinToString(",") { (role, pattern) -> summaryJson(role, pattern) }
    val details = templates.joinToString(",") { (role, pattern) -> detailJson(role, pattern) }
    return """{"templates":[$summaries],"details":[$details]}"""
}

/** The patterns most notification tests share: a screen, a second template for the same files, and a JSON file. */
internal val NOTIFICATION_JSON: String = placementJson(
    "feature.Screen" to "feature/\${feature}/ui/\${name}Screen.kt",
    "feature.AnyUi" to "feature/\${feature}/ui/\${name}.kt",
    "config.Json" to "config/\${name}.json",
)

private fun capturesJson(pattern: String): String {
    val segments = pattern.split('/')
    val glob = pattern.replace(CAPTURE, "*")
    return CAPTURE.findAll(pattern).map { it.groupValues[1] }.distinct().joinToString(",") { name ->
        val position = segments.indexOfFirst { "\${$name}" in it }
        """{"name":"$name","kind":"PathCapture","pattern":"$glob","segment":"${segments[position]}","position":$position}"""
    }
}

private fun summaryJson(role: String, pattern: String): String =
    """{"template":"$role","id":null,"title":"$role","roleName":"$role","summary":null,"parameterNames":[],"conflict":false,""" +
        """"captures":[${capturesJson(pattern)}]}"""

private fun detailJson(role: String, pattern: String): String {
    val names = CAPTURE.findAll(pattern).map { "\"${it.groupValues[1]}\"" }.distinct().joinToString(",")
    val fileName = pattern.substringAfterLast('/')
    return """{"template":"$role","id":null,"title":"$role","roleName":"$role","summary":null,"parameters":[],""" +
        """"files":[{"pattern":"$pattern","fileName":"$fileName","path":"$pattern","captures":[$names],"parameters":[],"content":""}],""" +
        """"branches":[],"exampleCommand":"./gradlew katachiTemplate --arg template=$role","captures":[${capturesJson(pattern)}]}"""
}
