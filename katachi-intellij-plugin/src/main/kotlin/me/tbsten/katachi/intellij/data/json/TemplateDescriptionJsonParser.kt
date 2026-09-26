package me.tbsten.katachi.intellij.data.json

import me.tbsten.katachi.intellij.model.BranchModel
import me.tbsten.katachi.intellij.model.FilePreviewModel
import me.tbsten.katachi.intellij.model.ParameterModel
import me.tbsten.katachi.intellij.model.TemplateDetailModel
import me.tbsten.katachi.intellij.model.TemplateModel
import me.tbsten.katachi.intellij.model.TemplateSummaryModel
import java.nio.file.Path

/**
 * Reads `templateDescription.json` (the contract in `.local/ide-plugin-impl/plan.md`, "JSON の契約")
 * into the list's templates, in `templates[]` order.
 *
 * Unknown keys are ignored. Every key of the contract is required, `null` only where the contract
 * allows it. A `kind` this plugin does not know makes that parameter an [ParameterModel.UnknownParam]
 * instead of failing the whole file (E-36). A `details[]` entry whose role is not in `templates[]`
 * is dropped, and a repeated role name keeps its first entry.
 *
 * @throws KatachiMalformedTemplateJsonException when [text] is not JSON (E-37).
 * @throws KatachiIncompatibleTemplateJsonException when a required key is missing or mistyped (E-35).
 */
internal fun parseTemplateDescriptionJson(text: String, file: Path): List<TemplateModel> {
    val root = JsonReader(parseJsonText(text, file), "$", file)
    val summaries = root.array("templates").map { it.summary() }.distinctBy { it.roleName }
    val details = root.array("details").map { it.detail() }.distinctBy { it.roleName }.associateBy { it.roleName }
    return summaries.map { TemplateModel(summary = it, detail = details[it.roleName]) }
}

/** A value at [location] in the document, read with the contract's types. */
private class JsonReader(private val value: JsonValue, private val location: String, private val file: Path) {

    private fun member(key: String): JsonReader {
        val obj = value as? JsonValue.JsonObject ?: throw mismatch(location, "object", value)
        val child = obj.members[key] ?: throw KatachiIncompatibleTemplateJsonException(file, "$location.$key", "a value", actual = null)
        return JsonReader(child, "$location.$key", file)
    }

    fun string(key: String): String = member(key).let { it.value as? JsonValue.JsonString ?: throw mismatch(it.location, "string", it.value) }.value

    fun nullableString(key: String): String? {
        val child = member(key)
        return when (val v = child.value) {
            JsonValue.JsonNull -> null
            is JsonValue.JsonString -> v.value
            else -> throw mismatch(child.location, "string or null", v)
        }
    }

    fun boolean(key: String): Boolean = member(key).let { it.value as? JsonValue.JsonBoolean ?: throw mismatch(it.location, "boolean", it.value) }.value

    fun nullableInt(key: String): Int? {
        val child = member(key)
        return when (val v = child.value) {
            JsonValue.JsonNull -> null
            is JsonValue.JsonNumber -> v.text.toIntOrNull() ?: throw mismatch(child.location, "integer or null", v)
            else -> throw mismatch(child.location, "integer or null", v)
        }
    }

    fun array(key: String): List<JsonReader> {
        val child = member(key)
        val array = child.value as? JsonValue.JsonArray ?: throw mismatch(child.location, "array", child.value)
        return array.elements.mapIndexed { index, element -> JsonReader(element, "${child.location}[$index]", file) }
    }

    fun strings(key: String): List<String> = array(key).map { element ->
        element.value as? JsonValue.JsonString ?: throw mismatch(element.location, "string", element.value)
    }.map { it.value }

    fun summary(): TemplateSummaryModel = TemplateSummaryModel(
        roleName = string("roleName"),
        title = nullableString("title"),
        summary = nullableString("summary"),
        parameterNames = strings("parameterNames"),
        fileCount = nullableInt("fileCount"),
    )

    fun detail(): TemplateDetailModel = TemplateDetailModel(
        roleName = string("roleName"),
        title = nullableString("title"),
        summary = nullableString("summary"),
        parameters = array("parameters").map { it.parameter() },
        files = array("files").map { it.file() },
        branches = array("branches").map { it.branch() },
        exampleCommand = string("exampleCommand"),
    )

    fun parameter(): ParameterModel {
        val name = string("name")
        val kind = string("kind")
        val typeName = string("typeName")
        val default = nullableString("default")
        val acceptedValues = strings("acceptedValues")
        val isRequired = boolean("isRequired")
        val previewValue = string("previewValue")
        // Required by the contract, but its value is only informative: an unknown one is ignored.
        string("previewValueSource")
        return when (kind) {
            ParameterModel.KIND_STRING -> ParameterModel.StringParam(name, typeName, default, isRequired, previewValue)
            ParameterModel.KIND_BOOLEAN -> ParameterModel.BooleanParam(name, typeName, default, isRequired, previewValue)
            ParameterModel.KIND_INT -> ParameterModel.IntParam(name, typeName, default, isRequired, previewValue)
            ParameterModel.KIND_ENUM -> ParameterModel.EnumParam(name, typeName, default, isRequired, previewValue, acceptedValues)
            else -> ParameterModel.UnknownParam(name, typeName, default, isRequired, previewValue, kindName = kind)
        }
    }

    fun file(): FilePreviewModel = FilePreviewModel(
        fileName = string("fileName"),
        path = nullableString("path"),
        unresolvedPatterns = strings("unresolvedPatterns"),
        content = string("content"),
    )

    fun branch(): BranchModel = BranchModel(
        parameterName = string("parameterName"),
        value = string("value"),
        addedFiles = strings("addedFiles"),
        removedFiles = strings("removedFiles"),
        addedParameters = array("addedParameters").map { it.parameter() },
        removedParameters = strings("removedParameters"),
    )

    private fun mismatch(at: String, expected: String, actual: JsonValue) =
        KatachiIncompatibleTemplateJsonException(file, at, expected, actual.typeName)
}
