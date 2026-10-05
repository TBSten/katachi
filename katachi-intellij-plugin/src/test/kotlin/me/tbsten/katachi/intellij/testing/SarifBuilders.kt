package me.tbsten.katachi.intellij.testing

import java.nio.file.Path

/** One result of a SARIF built by [sarifText]: the rule, and the file (relative to the project root) and line it points at. */
internal data class SarifResult(val kind: String, val uri: String, val line: Int = 1, val message: String = "$kind at $uri")

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
        """{"ruleId":"katachi.${result.kind}","ruleIndex":${kinds.indexOf(result.kind)},"level":"error","message":{"text":"${result.message}"},""" +
            """"locations":[{"physicalLocation":{"artifactLocation":{"uri":"${result.uri}","uriBaseId":"%SRCROOT%"},"region":{"startLine":${result.line}}}}],""" +
            """"properties":{"kind":"${result.kind}"}}"""
    }
    return """{"${'$'}schema":"https://json.schemastore.org/sarif-2.1.0.json","version":"2.1.0","runs":[{"tool":{"driver":{"name":"katachi","rules":[$rules]}},""" +
        """"originalUriBaseIds":{"%SRCROOT%":{"uri":"$base"}},"results":[$body]}]}"""
}
