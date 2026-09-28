package me.tbsten.katachi.intellij.uitest.pbt

import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import me.tbsten.katachi.intellij.model.BranchModel
import me.tbsten.katachi.intellij.model.CapturePlace
import me.tbsten.katachi.intellij.model.FilePreviewModel
import me.tbsten.katachi.intellij.model.KatachiModule
import me.tbsten.katachi.intellij.model.ParameterModel
import me.tbsten.katachi.intellij.model.TemplateDetailModel
import me.tbsten.katachi.intellij.model.TemplateModel
import me.tbsten.katachi.intellij.model.TemplateSummaryModel
import me.tbsten.katachi.intellij.testing.ContractFixtures
import me.tbsten.katachi.intellij.testing.module

/*
 * Definitions built from structure rather than taken from a sample: nested groups (0-3 levels),
 * roles at the root, every parameter type with and without a default, parameters inside `if`,
 * several files, a target that is not decided, same-named parameters of the same and of different
 * types, previews that failed, parameter kinds the plugin does not know, captures of a path and of
 * a module (named apart from the parameters, as katachi requires) shared between templates, and
 * several modules.
 * The specs stay small so that a shrunk counterexample reads as a sentence.
 */

internal enum class ParamType { Str, Bool, Int, Enum, Unknown }

internal data class ParamSpec(val name: String, val type: ParamType, val hasDefault: Boolean) {
    override fun toString(): String = "$name:$type${if (hasDefault) "=" else ""}"
}

/** What an `if` in the template does to its parameters and files. */
internal enum class BranchKind { None, BoolRemoves, BoolAdds, EnumAdds }

internal enum class TitleKind { None, Short, Long }

/** A `capture("name")` level of the file's directory, or with [module] the `*` of `:feature:*`. */
internal data class CaptureSpec(val name: String, val module: Boolean) {
    override fun toString(): String = if (module) ":$name" else "/$name"
}

internal data class TemplateSpec(
    val group: String,
    val params: List<ParamSpec>,
    val branch: BranchKind,
    val files: Int,
    val unresolved: Boolean,
    val previewFailed: Boolean,
    val title: TitleKind,
    val captures: List<CaptureSpec> = emptyList(),
) {
    override fun toString(): String = buildString {
        append("T(${group.ifEmpty { "<root>" }} $params")
        if (captures.isNotEmpty()) append(" captures=$captures")
        if (branch != BranchKind.None) append(" if=$branch")
        append(" files=$files")
        if (unresolved) append(" wildcard")
        if (previewFailed) append(" previewFailed")
        if (title != TitleKind.None) append(" title=$title")
        append(")")
    }
}

/**
 * One definition module and its templates, in the order the JSON lists them: built from [specs],
 * or a JSON katachi itself wrote ([fixture], one of `contract/json/`).
 */
internal data class ModuleDef(val module: KatachiModule, val specs: List<TemplateSpec>, val fixture: String? = null) {
    val templates: List<TemplateModel> by lazy { specs.mapIndexed { index, spec -> templateOf(index, spec) } }
    val json: String by lazy { fixture?.let(ContractFixtures::json) ?: contractJsonOf(templates) }

    override fun toString(): String = "${module.gradlePath}=${fixture ?: specs}"
}

/**
 * The definitions katachi wrote from :katachi's synthetic architectures: the structure alone, then
 * a second module next to it, then three hundred templates in its place.
 */
internal val syntheticCatalog: Catalog = Catalog(
    listOf(
        World(listOf(ModuleDef(module(":m0"), emptyList(), "synthetic-structure"))),
        World(listOf(ModuleDef(module(":m0"), emptyList(), "synthetic-structure"), ModuleDef(module(":m1"), emptyList(), "synthetic-second"))),
        World(listOf(ModuleDef(module(":m0"), emptyList(), "synthetic-many"))),
    ),
)

/** What the synced data and every load say at one moment. */
internal data class World(val modules: List<ModuleDef>) {
    override fun toString(): String = modules.joinToString(" | ")
}

/** How a later definition differs from the first: what a reload or a sync brings. */
internal enum class Change { Same, DropTemplate, RetypeParameters, AddTemplate, BreakPreview, AddModule, RemoveModule, DropAll }

/** The first definition and the ones later operations can switch to. */
internal data class Catalog(val worlds: List<World>) {
    val initial: World get() = worlds.first()

    override fun toString(): String = worlds.joinToString(prefix = "Catalog(", postfix = ")", separator = " / ")
}

private val GROUPS = listOf("", "", "a", "a.b", "a.b.c", "data", "domain.model")
private val PARAM_NAMES = listOf("name", "item", "count", "kind", "flag", "label")
private val MODULE_PATHS = listOf(":m0", ":m1", ":m2")

/** Never one of [PARAM_NAMES]: katachi refuses a capture named like a parameter. */
private val CAPTURE_NAMES = listOf("feature", "area")

internal val captureSpecArb: Arb<CaptureSpec> = arbitrary {
    CaptureSpec(name = Arb.element(CAPTURE_NAMES).bind(), module = Arb.int(0..3).bind() == 0)
}

internal val paramSpecArb: Arb<ParamSpec> = arbitrary {
    ParamSpec(
        name = Arb.element(PARAM_NAMES).bind(),
        type = Arb.element(ParamType.Str, ParamType.Str, ParamType.Bool, ParamType.Int, ParamType.Enum, ParamType.Str, ParamType.Unknown).bind(),
        hasDefault = Arb.boolean().bind(),
    )
}

internal val templateSpecArb: Arb<TemplateSpec> = arbitrary {
    TemplateSpec(
        group = Arb.element(GROUPS).bind(),
        params = Arb.list(paramSpecArb, 0..4).bind().distinctBy { it.name },
        branch = Arb.enum<BranchKind>().bind(),
        files = Arb.int(0..3).bind(),
        unresolved = Arb.int(0..9).bind() == 0,
        previewFailed = Arb.int(0..9).bind() == 0,
        title = Arb.enum<TitleKind>().bind(),
        // Most templates have none, as in a real definition.
        captures = if (Arb.int(0..2).bind() == 0) Arb.list(captureSpecArb, 1..2).bind().distinctBy { it.name } else emptyList(),
    )
}

internal fun moduleDefArb(index: Int, sizes: IntRange): Arb<ModuleDef> = arbitrary {
    ModuleDef(module(MODULE_PATHS[index]), Arb.list(templateSpecArb, sizes).bind())
}

/** 1-2 modules at first, then up to 3 later definitions, each one [Change] away from the one before. */
internal fun catalogArb(sizes: IntRange = 1..8): Arb<Catalog> = arbitrary(CatalogShrinker) {
    val moduleCount = Arb.int(1..2).bind()
    val first = World((0 until moduleCount).map { moduleDefArb(it, sizes).bind() })
    val worlds = mutableListOf(first)
    repeat(Arb.int(0..3).bind()) {
        // Weighted towards changes that take something away from what may be checked.
        val change = Arb.element(
            Change.DropTemplate, Change.DropTemplate, Change.DropTemplate, Change.BreakPreview, Change.BreakPreview,
            Change.RetypeParameters, Change.RetypeParameters, Change.AddTemplate, Change.Same, Change.AddModule, Change.RemoveModule,
            // Every template gone at once: DropTemplate alone empties only a small first module.
            Change.DropAll, Change.DropAll,
        ).bind()
        val extra = Arb.list(templateSpecArb, 1..3).bind()
        worlds += changed(worlds.last(), change, extra)
    }
    Catalog(worlds)
}

private fun changed(world: World, change: Change, extra: List<TemplateSpec>): World {
    val first = world.modules.first()
    fun withFirst(specs: List<TemplateSpec>) = World(listOf(first.copy(specs = specs)) + world.modules.drop(1))
    return when (change) {
        Change.Same -> world
        Change.DropTemplate -> withFirst(first.specs.drop(1))
        Change.RetypeParameters -> withFirst(first.specs.map { spec -> spec.copy(params = spec.params.map { it.copy(type = retyped(it.type)) }) })
        Change.AddTemplate -> withFirst(first.specs + extra)
        Change.BreakPreview -> withFirst(first.specs.mapIndexed { index, spec -> if (index == 0) spec.copy(previewFailed = true) else spec })
        Change.AddModule -> if (world.modules.size >= MODULE_PATHS.size) {
            world
        } else {
            World(world.modules + ModuleDef(module(MODULE_PATHS[world.modules.size]), extra))
        }
        Change.RemoveModule -> if (world.modules.size <= 1) world else World(world.modules.dropLast(1))
        Change.DropAll -> World(world.modules.map { it.copy(specs = emptyList()) })
    }
}

private fun retyped(type: ParamType): ParamType = when (type) {
    ParamType.Str -> ParamType.Int
    ParamType.Int -> ParamType.Str
    ParamType.Bool -> ParamType.Enum
    ParamType.Enum -> ParamType.Bool
    ParamType.Unknown -> ParamType.Str
}

private val ENUM_VALUES = listOf("A", "B", "C")

private fun parameterOf(spec: ParamSpec): ParameterModel {
    val required = !spec.hasDefault
    return when (spec.type) {
        ParamType.Str -> {
            // A default reading another parameter, as `default = "${name}Item"` does in a definition.
            val default = if (!spec.hasDefault) null else if (spec.name == "name") "Name" else "\${name}Item"
            ParameterModel.StringParam(spec.name, "String", default, required, "\${${spec.name}}")
        }
        ParamType.Bool -> {
            val default = if (spec.hasDefault) "false" else null
            ParameterModel.BooleanParam(spec.name, "Boolean", default, required, default ?: "true")
        }
        ParamType.Int -> ParameterModel.IntParam(spec.name, "Int", if (spec.hasDefault) "1" else null, required, if (spec.hasDefault) "1" else "0")
        ParamType.Enum -> {
            val default = if (spec.hasDefault) "B" else null
            ParameterModel.EnumParam(spec.name, "Kind", default, required, default ?: "A", ENUM_VALUES)
        }
        ParamType.Unknown -> ParameterModel.UnknownParam(spec.name, "List<String>", null, required, "[]", kindName = "ListParameter")
    }
}

/**
 * The template [spec] describes, as katachi would list it. A real katachi lists exactly one file
 * per template; [spec.files] still varies the count here, since the plugin's own model does not
 * enforce that (nothing in [FilePreviewModel] or the code that reads `files[]` assumes one), and
 * exercising several keeps this PBT's coverage of that generic list-handling code.
 */
internal fun templateOf(index: Int, spec: TemplateSpec): TemplateModel {
    val simpleName = "R$index"
    val roleName = if (spec.group.isEmpty()) simpleName else "${spec.group}.$simpleName"
    val parameters = spec.params.map(::parameterOf)
    val captures = spec.captures.map(::captureOf)
    val stem = if (spec.params.any { it.name == "name" }) "\${name}" else simpleName
    val captureDirs = spec.captures.joinToString("") { "/\${${it.name}}" }
    val parameterNames = parameters.map { it.name }
    val captureNames = captures.map { it.name }
    val files = (0 until spec.files).map { i ->
        val fileName = "${stem}F$i.kt"
        val directory = "mod/src/${spec.group.ifEmpty { "root" }}$captureDirs"
        if (i == 0 && spec.unresolved) {
            FilePreviewModel("mod/src/**/$fileName", fileName, null, captureNames, parameterNames, "// $fileName")
        } else {
            FilePreviewModel("$directory/$fileName", fileName, "$directory/$fileName", captureNames, parameterNames, "// $fileName")
        }
    }
    val title = when (spec.title) {
        TitleKind.None -> null
        TitleKind.Short -> "タイトル$index"
        TitleKind.Long -> "とても長いタイトル".repeat(6) + index
    }
    val shownTitle = title ?: roleName
    val summary = TemplateSummaryModel(roleName, null, shownTitle, roleName, null, parameterNames, spec.previewFailed, captureNames)
    if (spec.previewFailed) return TemplateModel(summary, detail = null)
    val detail = TemplateDetailModel(
        template = roleName,
        id = null,
        title = shownTitle,
        roleName = roleName,
        summary = null,
        parameters = parameters,
        files = files,
        branches = listOfNotNull(branchOf(spec, parameters, files)),
        exampleCommand = "./gradlew katachiTemplate --arg template=$roleName",
        captures = captures,
    )
    return TemplateModel(summary, detail)
}

/** Where [spec] sits: its own directory level under the group's, or the `*` of `:feature:*`. */
private fun captureOf(spec: CaptureSpec): ParameterModel.CaptureParam = ParameterModel.CaptureParam(
    spec.name,
    listOf(
        if (spec.module) {
            CapturePlace(CapturePlace.KIND_MODULE, ":feature:*", 0, "\${${spec.name}}")
        } else {
            CapturePlace(CapturePlace.KIND_PATH, "mod/src/*/*/*.kt", 2, "\${${spec.name}}")
        },
    ),
)

private fun branchOf(spec: TemplateSpec, parameters: List<ParameterModel>, files: List<FilePreviewModel>): BranchModel? = when (spec.branch) {
    BranchKind.None -> null
    BranchKind.BoolRemoves -> parameters.filterIsInstance<ParameterModel.BooleanParam>().firstOrNull()?.let { controller ->
        BranchModel(
            parameterName = controller.name,
            value = if (controller.previewValue == "true") "false" else "true",
            addedFiles = emptyList(),
            removedFiles = files.drop(1).takeLast(1).map { it.fileName },
            addedParameters = emptyList(),
            removedParameters = parameters.filterIsInstance<ParameterModel.StringParam>().filter { it.name != "name" }.take(1).map { it.name },
        )
    }
    BranchKind.BoolAdds -> parameters.filterIsInstance<ParameterModel.BooleanParam>().firstOrNull()?.let { controller ->
        BranchModel(
            parameterName = controller.name,
            value = if (controller.previewValue == "true") "false" else "true",
            addedFiles = listOf("ExtraOf${controller.name}.kt"),
            removedFiles = emptyList(),
            addedParameters = listOf(ParameterModel.StringParam("extra", "String", null, true, "\${extra}")),
            removedParameters = emptyList(),
        )
    }
    BranchKind.EnumAdds -> parameters.filterIsInstance<ParameterModel.EnumParam>().firstOrNull()?.let { controller ->
        BranchModel(
            parameterName = controller.name,
            value = ENUM_VALUES.first { it != controller.previewValue },
            addedFiles = emptyList(),
            removedFiles = emptyList(),
            addedParameters = listOf(ParameterModel.IntParam("variant", "Int", "2", false, "2")),
            removedParameters = emptyList(),
        )
    }
}
