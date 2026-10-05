package me.tbsten.katachi.intellij.testing

import java.nio.file.Path

/**
 * One result of a SARIF built by [sarifText]: the rule, and the file (relative to the project root) and line it points at
 * (`null`: no `region`, as for a file the check could not read).
 */
internal data class SarifResult(
    val kind: String,
    val uri: String,
    val line: Int? = 1,
    val message: String = "$kind at $uri",
    /** More `properties` (`role`, `constraint`, `declaration`, `declaredAt` …) next to `kind`. */
    val properties: Map<String, String> = emptyMap(),
    val level: String = "error",
)

/**
 * A minimal `katachi.sarif` (SARIF 2.1.0, one run) whose `%SRCROOT%` is [projectRoot], as katachi's
 * `report { sarif = true }` writes it: only the keys the plugin reads.
 *
 * ```kotlin
 * Files.writeString(sarif, sarifText(root, listOf(SarifResult("UnexpectedFile", "app/src/Stray.kt", 3))))
 * ```
 */
internal fun sarifText(projectRoot: Path, results: List<SarifResult>): String {
    val base = projectRoot.toUri().toString().let { if (it.endsWith("/")) it else "$it/" }
    val kinds = results.map { it.kind }.distinct()
    val rules = kinds.joinToString(",") { """{"id":"katachi.$it","name":"$it","defaultConfiguration":{"level":"error"}}""" }
    val body = results.joinToString(",") { result ->
        val region = result.line?.let { ""","region":{"startLine":$it}""" }.orEmpty()
        val properties = (mapOf("kind" to result.kind) + result.properties).entries.joinToString(",") { (key, value) -> "${jsonString(key)}:${jsonString(value)}" }
        """{"ruleId":"katachi.${result.kind}","ruleIndex":${kinds.indexOf(result.kind)},"level":"${result.level}","message":{"text":${jsonString(result.message)}},""" +
            """"locations":[{"physicalLocation":{"artifactLocation":{"uri":"${result.uri}","uriBaseId":"%SRCROOT%"}$region}}],""" +
            """"properties":{$properties}}"""
    }
    return """{"${'$'}schema":"https://json.schemastore.org/sarif-2.1.0.json","version":"2.1.0","runs":[{"tool":{"driver":{"name":"katachi","rules":[$rules]}},""" +
        """"originalUriBaseIds":{"%SRCROOT%":{"uri":"$base"}},"results":[$body]}]}"""
}

/** [text] as a JSON string literal: a real message holds line breaks and quotes. */
private fun jsonString(text: String): String = buildString {
    append('"')
    for (char in text) {
        when (char) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(char)
        }
    }
    append('"')
}
