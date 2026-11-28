package me.tbsten.katachi.script.internal

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull

// The part of katachi's definition JSON (`DefinitionJson` in :katachi, written by katachiInternalDefinitionJson)
// that lint reads. It is not shared with :katachi, whose JSON classes are internal, so a key renamed
// there must be renamed here: DefinitionContractSpec writes the JSON with the real processor and reads it back.

internal const val SUPPORTED_DEFINITION_SCHEMA_VERSION: Int = 1

internal class DefinitionJsonFormatException(message: String) : RuntimeException(message)

internal class DefinitionJsonTooNewException(val version: Int) : RuntimeException("schemaVersion $version")

/** Where a Role or group was declared. The JSON keeps the file name only. */
internal class DeclaredAt(val fileName: String, val line: Int)

/** [declaredByGradle]: gradle() declared it, so [declaredAt] is the line of the `gradle { }` call and the user wrote no `"Name".group { }` there. */
internal class DefinedGroup(
    val name: String,
    val path: List<String>,
    val documented: Boolean?,
    val declaredAt: DeclaredAt?,
    val declaredByGradle: Boolean,
)

/** `kind` is a LayoutEntryKind name: Directory, File, AnyFile or Ignore. */
internal class DefinedEntry(val path: String, val kind: String, val synthetic: Boolean)

internal class DefinedRole(
    val name: String,
    val groupPath: List<String>,
    val documented: Boolean?,
    val declaredAt: DeclaredAt?,
    val layout: List<DefinedEntry>,
    val constraints: Int,
    val declaredByGradle: Boolean,
)

internal class DefinitionModel(val groups: List<DefinedGroup>, val roles: List<DefinedRole>)

// A JSON from a katachi that does not write `declaredByGradle` yet: gradle()'s groups and roles, by the names it gives them.
private val GRADLE_GROUP_PATHS: Set<List<String>> = setOf(listOf("Gradle"), listOf("Gradle", "GradleWrapper"))

private val GRADLE_ROLE_NAMES: Set<String> = setOf(
    "LauncherScript",
    "WrapperJar",
    "WrapperProperties",
    "SettingsScript",
    "BuildScript",
    "GradleProperties",
    "VersionCatalog",
    "DaemonJvmProperties",
)

internal fun readDefinitionModel(text: String): DefinitionModel {
    val root = try {
        Json.parseToJsonElement(text)
    } catch (failure: SerializationException) {
        throw DefinitionJsonFormatException(failure.message.orEmpty())
    }
    val definition = root as? JsonObject ?: throw DefinitionJsonFormatException("not an object")
    val version = definition.intOf("schemaVersion")
    if (version > SUPPORTED_DEFINITION_SCHEMA_VERSION) throw DefinitionJsonTooNewException(version)
    return DefinitionModel(
        groups = definition.objects("groups").map { group ->
            val path = group.strings("path")
            DefinedGroup(
                name = group.stringOf("name"),
                path = path,
                documented = group.booleanOrNull("documented"),
                declaredAt = group.siteOrNull("declaredAt"),
                declaredByGradle = group.booleanOrNull("declaredByGradle") ?: (path in GRADLE_GROUP_PATHS),
            )
        },
        roles = definition.objects("roles").map { role ->
            val name = role.stringOf("name")
            val groupPath = role.strings("groupPath")
            DefinedRole(
                name = name,
                groupPath = groupPath,
                documented = role.booleanOrNull("documented"),
                declaredAt = role.siteOrNull("declaredAt"),
                layout = role.objects("layout").map { DefinedEntry(it.stringOf("path"), it.stringOf("kind"), it.booleanOrNull("synthetic") == true) },
                constraints = role.array("constraints").size,
                declaredByGradle = role.booleanOrNull("declaredByGradle") ?: (groupPath in GRADLE_GROUP_PATHS && name in GRADLE_ROLE_NAMES),
            )
        },
    )
}

private fun JsonObject.element(key: String): JsonElement = this[key] ?: throw DefinitionJsonFormatException("\"$key\" is missing")

private fun JsonObject.array(key: String): JsonArray = element(key) as? JsonArray ?: throw DefinitionJsonFormatException("\"$key\" is not an array")

private fun JsonObject.objects(key: String): List<JsonObject> =
    array(key).map { it as? JsonObject ?: throw DefinitionJsonFormatException("\"$key\" holds something that is not an object") }

private fun JsonObject.strings(key: String): List<String> =
    array(key).map { (it as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.content ?: throw DefinitionJsonFormatException("\"$key\" holds a non-string") }

private fun JsonObject.stringOf(key: String): String =
    (element(key) as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.content ?: throw DefinitionJsonFormatException("\"$key\" is not a string")

private fun JsonObject.intOf(key: String): Int =
    (element(key) as? JsonPrimitive)?.intOrNull ?: throw DefinitionJsonFormatException("\"$key\" is not an integer")

private fun JsonObject.booleanOrNull(key: String): Boolean? = (this[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.booleanOrNull

private fun JsonObject.siteOrNull(key: String): DeclaredAt? {
    val site = this[key] as? JsonObject ?: return null
    return DeclaredAt(site.stringOf("fileName"), site.intOf("line"))
}
