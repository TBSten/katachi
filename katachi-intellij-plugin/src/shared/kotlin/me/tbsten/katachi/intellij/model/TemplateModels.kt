package me.tbsten.katachi.intellij.model

/**
 * One entry of `templates[]` in `templateDescription.json`: what the list row shows even when the
 * preview failed.
 */
internal data class TemplateSummaryModel(
    /** Qualified role name such as `data/Repository`; the group is everything before the last `/`. */
    val roleName: String,
    val title: String?,
    val summary: String?,
    val parameterNames: List<String>,
    /** Files with the preview values; `null` when katachi could not preview the template. */
    val fileCount: Int?,
)

/** One entry of `details[]`: the parameters, files and branches of a template that previewed. */
internal data class TemplateDetailModel(
    val roleName: String,
    val title: String?,
    val summary: String?,
    val parameters: List<ParameterModel>,
    val files: List<FilePreviewModel>,
    val branches: List<BranchModel>,
    val exampleCommand: String,
)

/**
 * A template parameter. The subtype picks the input widget; [UnknownParam] is a `kind` this plugin
 * does not know (a newer katachi), which makes the whole template unusable (E-36).
 */
internal sealed interface ParameterModel {
    val name: String
    val typeName: String

    /** The `--arg` spelling of the default, possibly holding `${other}` placeholders; `null` when required. */
    val default: String?
    val isRequired: Boolean
    val previewValue: String

    /** The JSON `kind`, which together with [typeName] decides whether two same-named fields link. */
    val kindName: String

    data class StringParam(
        override val name: String,
        override val typeName: String,
        override val default: String?,
        override val isRequired: Boolean,
        override val previewValue: String,
    ) : ParameterModel {
        override val kindName: String get() = KIND_STRING
    }

    data class BooleanParam(
        override val name: String,
        override val typeName: String,
        override val default: String?,
        override val isRequired: Boolean,
        override val previewValue: String,
    ) : ParameterModel {
        override val kindName: String get() = KIND_BOOLEAN
    }

    data class IntParam(
        override val name: String,
        override val typeName: String,
        override val default: String?,
        override val isRequired: Boolean,
        override val previewValue: String,
    ) : ParameterModel {
        override val kindName: String get() = KIND_INT
    }

    data class EnumParam(
        override val name: String,
        override val typeName: String,
        override val default: String?,
        override val isRequired: Boolean,
        override val previewValue: String,
        /** Entry names in declaration order. */
        val acceptedValues: List<String>,
    ) : ParameterModel {
        override val kindName: String get() = KIND_ENUM
    }

    data class UnknownParam(
        override val name: String,
        override val typeName: String,
        override val default: String?,
        override val isRequired: Boolean,
        override val previewValue: String,
        override val kindName: String,
    ) : ParameterModel

    companion object {
        const val KIND_STRING: String = "StringParameter"
        const val KIND_BOOLEAN: String = "BooleanParameter"
        const val KIND_INT: String = "IntParameter"
        const val KIND_ENUM: String = "EnumParameter"
    }
}

/** A file the template produces with the preview values, `${name}` placeholders included. */
internal data class FilePreviewModel(
    val fileName: String,
    /** Relative to the project root, `/` separated; `null` when the target is not decided (a wildcard). */
    val path: String?,
    val unresolvedPatterns: List<String>,
    val content: String,
)

/** What changes when one parameter takes [value] instead of its preview value. */
internal data class BranchModel(
    val parameterName: String,
    val value: String,
    val addedFiles: List<String>,
    val removedFiles: List<String>,
    val addedParameters: List<ParameterModel>,
    val removedParameters: List<String>,
)

/** Why a template row cannot be checked. */
internal sealed interface TemplateUnavailability {
    /** `details[]` has no entry: katachi could not preview it (E-07). */
    data object PreviewFailed : TemplateUnavailability

    /** A parameter has a `kind` this plugin does not know (E-36). */
    data class UnknownParameterKind(val kindNames: List<String>) : TemplateUnavailability
}

/** A template of the list: its summary, and its detail when the preview succeeded. */
internal data class TemplateModel(
    val summary: TemplateSummaryModel,
    val detail: TemplateDetailModel?,
) {
    val roleName: String get() = summary.roleName

    /** The last segment of [roleName], shown as the row's name. */
    val simpleName: String get() = roleName.substringAfterLast('/')

    /** Everything before the last `/` of [roleName]; empty for a top level role. */
    val groupPath: String get() = roleName.substringBeforeLast('/', missingDelimiterValue = "")

    val unavailability: TemplateUnavailability?
        get() {
            val detail = detail ?: return TemplateUnavailability.PreviewFailed
            val unknown = allParametersOf(detail).filterIsInstance<ParameterModel.UnknownParam>()
            return if (unknown.isEmpty()) null else TemplateUnavailability.UnknownParameterKind(unknown.map { it.kindName }.distinct())
        }

    val isAvailable: Boolean get() = unavailability == null
}

/**
 * Every parameter [detail] can show: the preview's own, then those that only branches add, each
 * name once.
 */
internal fun allParametersOf(detail: TemplateDetailModel): List<ParameterModel> {
    val byName = LinkedHashMap<String, ParameterModel>()
    for (parameter in detail.parameters) byName.putIfAbsent(parameter.name, parameter)
    for (branch in detail.branches) {
        for (parameter in branch.addedParameters) byName.putIfAbsent(parameter.name, parameter)
    }
    return byName.values.toList()
}
