package me.tbsten.katachi.intellij.model

/**
 * One entry of `templates[]` in `templateDescription.json`: what the list row shows even when the
 * preview failed.
 */
internal data class TemplateSummaryModel(
    /** The complete specifier `--arg template=` accepts, `data.Repository.repositoryImpl`; the element's key. */
    val template: String,
    /** This template's own `id`, or `null` for the one template of a role that declares no other. */
    val id: String?,
    /** `.template { }`'s own title, falling back to [id], then to the role's title; never blank. */
    val title: String,
    /** The role this template belongs to, qualified, `.` separated; the group is everything before the last segment. */
    val roleName: String,
    val summary: String?,
    val parameterNames: List<String>,
    /**
     * Whether this template's preview could not be built. Still listed (E-07): a template that
     * cannot be previewed is not one that cannot be run.
     */
    val conflict: Boolean,
    /** The names of this template's captures, each once; empty for a katachi that does not list them. */
    val captureNames: List<String> = emptyList(),
)

/** One entry of `details[]`: the parameters, file and branches of a template that previewed. */
internal data class TemplateDetailModel(
    val template: String,
    val id: String?,
    val title: String,
    val roleName: String,
    val summary: String?,
    val parameters: List<ParameterModel>,
    /** This template's one file, in a list for the JSON's sake. */
    val files: List<FilePreviewModel>,
    val branches: List<BranchModel>,
    val exampleCommand: String,
    /**
     * This template's named wildcards (`captures` of the JSON, kept apart from [parameters] there),
     * one per name. Empty for a katachi that does not list them.
     */
    val captures: List<ParameterModel.CaptureParam> = emptyList(),
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

    /**
     * A named wildcard of the role's layout (`capture("feature")`, `":feature:*".module(capture = ...)`).
     * The template does not declare it, but a run takes it as `--arg` like a String parameter: it
     * is always required and has no default. [places] lists where it sits, in the JSON's order.
     */
    data class CaptureParam(
        override val name: String,
        val places: List<CapturePlace>,
    ) : ParameterModel {
        override val typeName: String get() = "String"
        override val default: String? get() = null
        override val isRequired: Boolean get() = true
        override val previewValue: String get() = "\${$name}"
        override val kindName: String get() = KIND_CAPTURE
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

        /** Not a JSON `kind` of a parameter: the link key of captures, which only link to captures. */
        const val KIND_CAPTURE: String = "Capture"
    }
}

/**
 * One place a capture sits. [pattern] is the flattened file pattern with a `*` for each wildcard
 * level, or a module key such as `:feature:*`; [position] is the capture's `/`-separated level of
 * the file pattern, or which `*` of the module key it is; [segment] is the pattern of the one
 * segment this capture sits in, `${fileName}` in place of every capture that segment holds.
 */
internal data class CapturePlace(
    /** The JSON `kind`: `PathCapture`, `ModuleCapture`, or one a newer katachi added. */
    val kindName: String,
    val pattern: String,
    val position: Int,
    val segment: String,
) {
    val isModule: Boolean get() = kindName == KIND_MODULE

    companion object {
        const val KIND_PATH: String = "PathCapture"
        const val KIND_MODULE: String = "ModuleCapture"
    }
}

/** The one file the template produces with the preview values, `${name}` placeholders included. */
internal data class FilePreviewModel(
    /** The declared pattern, `/` separated, with every capture written as `${name}`. */
    val pattern: String,
    val fileName: String,
    /** Relative to the project root, `/` separated; `null` when the target is not decided (a wildcard). */
    val path: String?,
    /** This template's captures that appear on [pattern], by name. */
    val captures: List<String>,
    /** This template's parameters, by name. */
    val parameters: List<String>,
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
    /** `conflict: true` in the summary and no matching entry in `details[]`: the preview failed (E-07). */
    data object PreviewFailed : TemplateUnavailability

    /** A parameter has a `kind` this plugin does not know (E-36). */
    data class UnknownParameterKind(val kindNames: List<String>) : TemplateUnavailability
}

/** A template of the list: its summary, and its detail when the preview succeeded. */
internal data class TemplateModel(
    val summary: TemplateSummaryModel,
    val detail: TemplateDetailModel?,
) {
    /** The complete specifier `--arg template=` accepts for this template. */
    val template: String get() = summary.template
    val roleName: String get() = summary.roleName

    /** `.template { }`'s own title, falling back to `id`, then to the role's title: the row's name. */
    val title: String get() = summary.title

    /** Everything before the last `.` of [roleName]; empty for a top level role. */
    val groupPath: String get() = roleName.substringBeforeLast('.', missingDelimiterValue = "")

    val unavailability: TemplateUnavailability?
        get() {
            if (summary.conflict || detail == null) return TemplateUnavailability.PreviewFailed
            val unknown = allParametersOf(detail).filterIsInstance<ParameterModel.UnknownParam>()
            return if (unknown.isEmpty()) null else TemplateUnavailability.UnknownParameterKind(unknown.map { it.kindName }.distinct())
        }

    val isAvailable: Boolean get() = unavailability == null
}

/**
 * Every field [detail] can show: its captures first (they decide where the files go), then the
 * preview's own parameters, then those that only branches add, each name once.
 */
internal fun allParametersOf(detail: TemplateDetailModel): List<ParameterModel> {
    val byName = LinkedHashMap<String, ParameterModel>()
    for (capture in detail.captures) byName.putIfAbsent(capture.name, capture)
    for (parameter in detail.parameters) byName.putIfAbsent(parameter.name, parameter)
    for (branch in detail.branches) {
        for (parameter in branch.addedParameters) byName.putIfAbsent(parameter.name, parameter)
    }
    return byName.values.toList()
}
