package me.tbsten.katachi.intellij.data.sarif

import me.tbsten.katachi.intellij.data.generate.pathOfFileUri
import me.tbsten.katachi.intellij.data.json.JsonValue
import me.tbsten.katachi.intellij.data.json.KatachiMalformedTemplateJsonException
import me.tbsten.katachi.intellij.data.json.parseJsonText
import me.tbsten.katachi.intellij.model.ModuleId
import me.tbsten.katachi.intellij.model.SarifLevel
import me.tbsten.katachi.intellij.model.SarifLocation
import me.tbsten.katachi.intellij.model.ViolationModel
import java.net.URLDecoder
import java.nio.file.InvalidPathException
import java.nio.file.Path

private const val SARIF_VERSION: String = "2.1.0"
private const val SRCROOT: String = "%SRCROOT%"

/**
 * Reads the results of one `katachi.sarif` (design 4.7), only what the IDE shows: the rules'
 * `id` / `name` / default `level`, the base `originalUriBaseIds["%SRCROOT%"]`, and each result's
 * `ruleId`, `level`, `message.text`, `locations[0]`, `relatedLocations[]` and `properties`. Everything
 * else is ignored, and a key a result lacks reads as empty rather than failing the file.
 *
 * A location's `uri` becomes an absolute path: `%SRCROOT%` is joined to the base, `file:///` is taken
 * as it is, and each segment is percent-decoded and the result normalized (`.` is the project root). A path that does not exist is kept (`MissingFile`
 * points at one). A location whose `uri` cannot be made absolute (relative, no base) is dropped.
 *
 * ```kotlin
 * val violations = parseSarifJson(path.readText(), path, module.id)
 * val here = violations.filter { it.location?.path == file.toNioPath() }
 * ```
 *
 * @throws KatachiMalformedSarifException when [text] is not JSON.
 * @throws KatachiUnsupportedSarifVersionException when `version` is not `2.1.0`.
 * @throws KatachiIncompatibleSarifException when `runs` is missing.
 */
internal fun parseSarifJson(text: String, file: Path, module: ModuleId): List<ViolationModel> {
    val document = try {
        parseJsonText(text, file)
    } catch (failure: KatachiMalformedTemplateJsonException) {
        throw KatachiMalformedSarifException(file, failure.problem)
    }
    val version = document.string("version")
    if (version != SARIF_VERSION) throw KatachiUnsupportedSarifVersionException(file, version)
    val runs = document.array("runs") ?: throw KatachiIncompatibleSarifException(file, "runs", "an array")
    return runs.flatMap { run -> readRun(run, module) }
}

private class SarifRule(val id: String, val name: String?, val level: SarifLevel?)

private fun readRun(run: JsonValue, module: ModuleId): List<ViolationModel> {
    val rules = run.obj("tool").obj("driver").array("rules").orEmpty().map { rule ->
        SarifRule(
            id = rule.string("id").orEmpty(),
            name = rule.string("name"),
            level = rule.obj("defaultConfiguration").string("level")?.let { SarifLevel.of(it) },
        )
    }
    val base: Path? = run.obj("originalUriBaseIds").obj(SRCROOT).string("uri")?.let { pathOfFileUri(it) }
    return run.array("results").orEmpty().map { result ->
        val indexed = rules.getOrNull(result.int("ruleIndex") ?: -1)
        val ruleId = result.string("ruleId") ?: indexed?.id.orEmpty()
        val rule = indexed ?: rules.firstOrNull { it.id == ruleId }
        ViolationModel(
            module = module,
            ruleId = ruleId,
            label = rule?.name ?: ruleId.substringAfterLast('.'),
            level = result.string("level")?.let { SarifLevel.of(it) } ?: rule?.level ?: SarifLevel.Warning,
            message = result.obj("message").string("text").orEmpty(),
            location = result.array("locations").orEmpty().firstOrNull()?.let { locationOf(it, base) },
            related = result.array("relatedLocations").orEmpty().mapNotNull { locationOf(it, base) },
            properties = propertiesOf(result.obj("properties")),
        )
    }
}

private fun locationOf(location: JsonValue, base: Path?): SarifLocation? {
    val physical = location.obj("physicalLocation")
    val artifact = physical.obj("artifactLocation")
    val uri = artifact.string("uri") ?: return null
    val path = pathOfUri(uri, artifact.string("uriBaseId"), base) ?: return null
    return SarifLocation(path, physical.obj("region").int("startLine"))
}

/** [uri] as an absolute path, or `null` when it is relative and [uriBaseId] is not one this reads (or [base] is missing). */
private fun pathOfUri(uri: String, uriBaseId: String?, base: Path?): Path? {
    if (uri.startsWith("file:")) return pathOfFileUri(uri)
    if (uriBaseId != SRCROOT || base == null) return null
    return try {
        // Percent-decoding by segment: a `+` in a name is a plus, not a space.
        // normalize(): katachi writes `.` for the project root itself (`SarifLocations`), which must not stay in the path.
        uri.split('/').filter { it.isNotEmpty() }.fold(base) { path, segment -> path.resolve(decodeSegment(segment)) }.normalize()
    } catch (_: InvalidPathException) {
        null
    }
}

private fun decodeSegment(segment: String): String =
    if ('%' !in segment) segment else try {
        URLDecoder.decode(segment.replace("+", "%2B"), Charsets.UTF_8)
    } catch (_: IllegalArgumentException) {
        segment
    }

/** The `properties` object as text: a string stays, a number or boolean is written as it is, `null` and nested values are left out. */
private fun propertiesOf(properties: JsonValue?): Map<String, String> {
    val members = (properties as? JsonValue.JsonObject)?.members ?: return emptyMap()
    val result = LinkedHashMap<String, String>()
    for ((key, value) in members) {
        when (value) {
            is JsonValue.JsonString -> result[key] = value.value
            is JsonValue.JsonNumber -> result[key] = value.text
            is JsonValue.JsonBoolean -> result[key] = value.value.toString()
            else -> Unit
        }
    }
    return result
}

private fun JsonValue?.obj(key: String): JsonValue? = (this as? JsonValue.JsonObject)?.members?.get(key)?.takeIf { it is JsonValue.JsonObject }

private fun JsonValue?.array(key: String): List<JsonValue>? =
    ((this as? JsonValue.JsonObject)?.members?.get(key) as? JsonValue.JsonArray)?.elements

private fun JsonValue?.string(key: String): String? =
    ((this as? JsonValue.JsonObject)?.members?.get(key) as? JsonValue.JsonString)?.value

private fun JsonValue?.int(key: String): Int? =
    ((this as? JsonValue.JsonObject)?.members?.get(key) as? JsonValue.JsonNumber)?.text?.toIntOrNull()
