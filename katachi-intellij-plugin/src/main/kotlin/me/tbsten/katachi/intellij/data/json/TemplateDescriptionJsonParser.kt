package me.tbsten.katachi.intellij.data.json

import me.tbsten.katachi.intellij.model.BranchModel
import me.tbsten.katachi.intellij.model.CapturePlace
import me.tbsten.katachi.intellij.model.FilePreviewModel
import me.tbsten.katachi.intellij.model.ModuleChoiceModel
import me.tbsten.katachi.intellij.model.ModulePlacementModel
import me.tbsten.katachi.intellij.model.ParameterModel
import me.tbsten.katachi.intellij.model.SetArgModel
import me.tbsten.katachi.intellij.model.TemplateDetailModel
import me.tbsten.katachi.intellij.model.TemplateModel
import me.tbsten.katachi.intellij.model.TemplateSummaryModel
import java.nio.file.Path

/**
 * Reads `templateDescription.json` (design draft section 6, "`DescribeTemplates` / `katachiTemplates`
 * の出力と IDE の JSON") into the list's templates, in `templates[]` order, one entry per template
 * (not per role: a role with two templates is two entries sharing a `roleName`).
 *
 * Unknown keys are ignored. Every key of the contract is required, `null` only where the contract
 * allows it -- except `captures`, which a katachi from before `capture()` does not write: its
 * absence reads as no captures, and `modulePlacements`, which a katachi from before it does not
 * write: its absence leaves a file below a module capture undecided, as it used to be.
 * A `modulePlacements[]` entry goes to its template's file whose `path` is `null`
 * ([FilePreviewModel.modulePlacement]) and to the fields of its captures
 * ([ParameterModel.CaptureParam.existingModules]). A `kind` this plugin does not know makes that parameter an [ParameterModel.UnknownParam]
 * instead of failing the whole file (E-36). A `details[]` entry whose `template` is not in
 * `templates[]` is dropped, and a repeated `template` keeps its first entry.
 *
 * @throws KatachiMalformedTemplateJsonException when [text] is not JSON (E-37).
 * @throws KatachiIncompatibleTemplateJsonException when a required key is missing or mistyped (E-35).
 */
internal fun parseTemplateDescriptionJson(text: String, file: Path): List<TemplateModel> =
    parseTemplateDescriptionJson(parseJsonText(text, file), file)

/**
 * [parseTemplateDescriptionJson] over a document that is already parsed: `definition.json` carries
 * the same object under its `templates` key, and reads it through here. [file] is for error messages;
 * a location in them starts at the given [document] (`$`).
 */
internal fun parseTemplateDescriptionJson(document: JsonValue, file: Path): List<TemplateModel> {
    val root = JsonReader(document, "$", file)
    val summaries = root.array("templates").map { it.summary() }.distinctBy { it.template }
    val placements = root.optionalArray("modulePlacements").map { it.modulePlacement() }.distinctBy { it.first }.toMap()
    val details = root.array("details").map { it.detail() }.distinctBy { it.template }
        .map { detail -> placements[detail.template]?.let { detail.placedIn(it) } ?: detail }
        .associateBy { it.template }
    val templateRows = summaries.map { TemplateModel(summary = it, detail = details[it.template]) }
    return templateRows + templateSetRowsOf(root, placements)
}

/**
 * The template sets of `templateSets[]` / `templateSetDetails[]`, one row each, in `templateSets[]`
 * order. Both keys are absent in a katachi from before template sets: that reads as no sets. A set
 * without a matching `templateSetDetails[]` entry is a row without a detail (its preview failed).
 */
private fun templateSetRowsOf(root: JsonReader, placements: Map<String, ModulePlacementModel>): List<TemplateModel> {
    val summaries = root.optionalArray("templateSets").map { it.setSummary() }.distinctBy { it.specifier }
    val details = root.optionalArray("templateSetDetails").map { it.setDetail() }.distinctBy { it.specifier }.associateBy { it.specifier }
    return summaries.map { summary -> templateSetRow(summary, details[summary.specifier], placements) }
}

/**
 * [this] with [placement] given to the file whose path the declarations leave open, and the values
 * each module capture can take given to its field. A file that already has a path is left alone:
 * the placement only answers what `path: null` asks.
 */
private fun TemplateDetailModel.placedIn(placement: ModulePlacementModel): TemplateDetailModel = copy(
    files = files.map { file -> if (file.path == null) file.copy(modulePlacement = placement) else file },
    captures = captures.map { capture ->
        val index = placement.captureNames.indexOf(capture.name)
        if (index < 0) capture else capture.copy(existingModules = placement.modules.mapNotNull { it.values.getOrNull(index) }.distinct())
    },
)

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

    /** [array] of [key], or empty when the object has no such key (a key added to the contract later). */
    fun optionalArray(key: String): List<JsonReader> {
        val obj = value as? JsonValue.JsonObject ?: throw mismatch(location, "object", value)
        return if (key in obj.members) array(key) else emptyList()
    }

    private fun hasKey(key: String): Boolean {
        val obj = value as? JsonValue.JsonObject ?: throw mismatch(location, "object", value)
        return key in obj.members
    }

    /** [int] of [key], or [default] when the object has no such key. */
    fun optionalInt(key: String, default: Int): Int = if (hasKey(key)) int(key) else default

    /** [nullableString] of [key], or `null` when the object has no such key. */
    fun optionalNullableString(key: String): String? = if (hasKey(key)) nullableString(key) else null

    fun int(key: String): Int {
        val child = member(key)
        val number = child.value as? JsonValue.JsonNumber ?: throw mismatch(child.location, "integer", child.value)
        return number.text.toIntOrNull() ?: throw mismatch(child.location, "integer", number)
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
        template = string("template"),
        id = nullableString("id"),
        title = string("title"),
        roleName = string("roleName"),
        summary = nullableString("summary"),
        parameterNames = strings("parameterNames"),
        conflict = boolean("conflict"),
        // The `--arg`s a run takes: the plain places only, as `details[].captures` is read.
        captureNames = optionalArray("captures").filter { it.optionalNullableString("transform") == null }.map { it.string("name") }.distinct(),
    )

    fun setSummary(): SetSummaryJson = SetSummaryJson(
        specifier = string("templateSet"),
        name = string("name"),
        title = string("title"),
        summary = nullableString("summary"),
        groupName = nullableString("groupName"),
        parameterNames = strings("parameterNames"),
        conflict = boolean("conflict"),
    )

    fun setDetail(): SetDetailJson = SetDetailJson(
        specifier = string("templateSet"),
        parameters = array("parameters").map { it.parameter() },
        members = array("members").map { member ->
            SetMemberJson(
                template = member.string("template"),
                title = member.string("title"),
                args = member.array("args").map { arg ->
                    SetArgModel(
                        name = arg.string("name"),
                        isCapture = arg.string("kind") == SET_ARG_KIND_CAPTURE,
                        previewValue = arg.string("previewValue"),
                        parameter = arg.nullableString("parameter"),
                    )
                },
                files = member.array("files").map { it.file() },
            )
        },
        exampleCommand = string("exampleCommand"),
    )

    fun detail(): TemplateDetailModel = TemplateDetailModel(
        template = string("template"),
        id = nullableString("id"),
        title = string("title"),
        roleName = string("roleName"),
        summary = nullableString("summary"),
        parameters = array("parameters").map { it.parameter() },
        files = array("files").map { it.file() },
        branches = array("branches").map { it.branch() },
        exampleCommand = string("exampleCommand"),
        captures = captures(),
    )

    /**
     * `captures`, one [ParameterModel.CaptureParam] per name: katachi lists a name once per place. A name with derived
     * places only (`<x.pascalCase>` without a plain `${x}`) is not a field: katachi never writes one, but it is not a
     * `--arg` either, so it is left out rather than asked for.
     */
    private fun captures(): List<ParameterModel.CaptureParam> {
        val places = LinkedHashMap<String, MutableList<CapturePlace>>()
        for (capture in optionalArray("captures")) {
            val place = CapturePlace(
                kindName = capture.string("kind"),
                pattern = capture.string("pattern"),
                position = capture.int("position"),
                segment = capture.string("segment"),
                wildcardIndex = capture.optionalInt("wildcardIndex", default = 0),
                transform = capture.optionalNullableString("transform"),
            )
            places.getOrPut(capture.string("name")) { mutableListOf() } += place
        }
        return places.filter { (_, list) -> list.any { it.transform == null } }.map { (name, list) -> ParameterModel.CaptureParam(name, list.distinct()) }
    }

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
        pattern = string("pattern"),
        fileName = string("fileName"),
        path = nullableString("path"),
        captures = strings("captures"),
        parameters = strings("parameters"),
        content = string("content"),
    )

    /** One `modulePlacements[]` entry, keyed by its template's specifier. */
    fun modulePlacement(): Pair<String, ModulePlacementModel> = string("template") to ModulePlacementModel(
        modulePattern = string("modulePattern"),
        captureNames = strings("captureNames"),
        modules = array("modules").map { module ->
            ModuleChoiceModel(
                values = module.strings("values"),
                modulePath = module.string("modulePath"),
                directory = module.string("directory"),
                path = module.string("path"),
            )
        },
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
