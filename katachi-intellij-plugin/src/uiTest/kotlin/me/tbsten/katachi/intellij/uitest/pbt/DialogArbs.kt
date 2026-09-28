package me.tbsten.katachi.intellij.uitest.pbt

import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.choose
import io.kotest.property.arbitrary.constant
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import me.tbsten.katachi.intellij.model.BranchModel
import me.tbsten.katachi.intellij.model.KatachiModule
import me.tbsten.katachi.intellij.model.ParameterModel
import me.tbsten.katachi.intellij.model.TemplateModel
import me.tbsten.katachi.intellij.testing.ContractFixtures
import me.tbsten.katachi.intellij.testing.capture
import me.tbsten.katachi.intellij.testing.file
import me.tbsten.katachi.intellij.testing.template
import me.tbsten.katachi.intellij.uitest.pbt.placement.Part
import me.tbsten.katachi.intellij.uitest.pbt.placement.PatternSpec
import me.tbsten.katachi.intellij.uitest.pbt.placement.Seg
import me.tbsten.katachi.intellij.uitest.pbt.placement.moduleOf
import me.tbsten.katachi.intellij.uitest.pbt.placement.patternArb

/*
 * Definitions for the generate dialog, built from structure. A template is a file pattern
 * (fixed segments, captures alone in a segment, partial captures, the capture of a module and what
 * is derived from it) with a role (nested groups or at the root; two templates of one role), the
 * parameters of every type (none to six, with and without a default), a branch that removes or adds
 * parameters, and sometimes a preview that failed or a parameter kind this plugin does not know.
 * The real JSON of the three samples goes through the same machine as [realWorlds].
 *
 * New file so that the catalogs of the tool window ([CatalogArbs.kt]) stay as they were.
 */

/** What is wrong with a template that cannot be offered in the dialog. */
internal enum class Flaw { None, PreviewFailed, UnknownKind }

internal data class DialogTemplateSpec(
    val pattern: PatternSpec,
    val group: String,
    /** Templates with the same key are two templates of one role (told apart by an id). */
    val roleKey: Int,
    val params: List<ParamSpec>,
    val branch: BranchKind,
    val flaw: Flaw,
) {
    override fun toString(): String = "T($group#$roleKey $pattern ${params.ifEmpty { "-" }}${if (branch != BranchKind.None) " if=$branch" else ""}${if (flaw != Flaw.None) " $flaw" else ""})"
}

/** One definition module: [pool] is every template it can ever list, in the order the JSON lists them. */
internal data class DialogDefinition(val module: KatachiModule, val pool: List<TemplateModel>, val label: String)

internal data class DialogWorld(val definitions: List<DialogDefinition>, val label: String) {
    override fun toString(): String = "$label[" + definitions.joinToString(" | ") { "${it.module.gradlePath}=${it.label}" } + "]"
}

private val GROUPS = listOf("", "", "data", "domain.model", "ui", "a.b.c")

private val dialogParamArb: Arb<ParamSpec> = paramSpecArb.map { if (it.type == ParamType.Unknown) it.copy(type = ParamType.Str) else it }

private val siblingFiles = listOf(
    Seg(listOf(Part.Lit("Foo.kt"))),
    Seg(listOf(Part.Cap("s0"), Part.Lit("Test.kt"))),
    Seg(listOf(Part.Lit("Home"), Part.Cap("s0"), Part.Lit("Screen.kt"))),
)

/** The `<x>` of a module is always there for a capture of that module: katachi refuses a name it cannot derive from. */
private val cleanPatternArb: Arb<PatternSpec> = patternArb.filter { spec ->
    spec.segs.none { seg -> seg.parts.any { part -> part is Part.Der && part.name !in spec.moduleNames } }
}

private fun templateSpecArb(first: Boolean, previous: List<DialogTemplateSpec>): Arb<DialogTemplateSpec> = arbitrary {
    val r = it.random
    // A sibling in the directory of an earlier template: the New menu and the notification then offer several at one place.
    val pattern = if (previous.isNotEmpty() && r.nextInt(3) == 0) {
        val base = previous[r.nextInt(previous.size)].pattern
        PatternSpec(base.segs.dropLast(1) + siblingFiles[r.nextInt(siblingFiles.size)])
    } else {
        cleanPatternArb.bind()
    }
    val roleKey = if (previous.isNotEmpty() && r.nextInt(4) == 0) previous[r.nextInt(previous.size)].roleKey else previous.size + 1
    val flaw = when {
        first -> Flaw.None
        r.nextInt(10) == 0 -> Flaw.PreviewFailed
        r.nextInt(12) == 0 -> Flaw.UnknownKind
        else -> Flaw.None
    }
    DialogTemplateSpec(
        pattern = pattern,
        group = previous.firstOrNull { p -> p.roleKey == roleKey }?.group ?: GROUPS[r.nextInt(GROUPS.size)],
        roleKey = roleKey,
        params = Arb.list(dialogParamArb, 0..6).bind().distinctBy { p -> p.name },
        branch = Arb.enum<BranchKind>().bind(),
        flaw = flaw,
    )
}

private fun parameterOf(spec: ParamSpec): ParameterModel {
    val required = !spec.hasDefault
    return when (spec.type) {
        ParamType.Str -> ParameterModel.StringParam(spec.name, "String", if (spec.hasDefault) "Default" else null, required, "\${${spec.name}}")
        ParamType.Bool -> ParameterModel.BooleanParam(spec.name, "Boolean", if (spec.hasDefault) "false" else null, required, if (spec.hasDefault) "false" else "true")
        ParamType.Int -> ParameterModel.IntParam(spec.name, "Int", if (spec.hasDefault) "1" else null, required, if (spec.hasDefault) "1" else "0")
        ParamType.Enum -> ParameterModel.EnumParam(spec.name, "Kind", if (spec.hasDefault) "B" else null, required, if (spec.hasDefault) "B" else "A", listOf("A", "B", "C"))
        ParamType.Unknown -> ParameterModel.UnknownParam(spec.name, "List<String>", null, required, "[]", kindName = "ListParameter")
    }
}

private fun branchOf(kind: BranchKind, parameters: List<ParameterModel>): BranchModel? {
    val bool = parameters.filterIsInstance<ParameterModel.BooleanParam>().firstOrNull()
    val enum = parameters.filterIsInstance<ParameterModel.EnumParam>().firstOrNull()
    return when (kind) {
        BranchKind.None -> null
        BranchKind.BoolRemoves -> bool?.let {
            val removed = parameters.filterIsInstance<ParameterModel.StringParam>().take(1).map { p -> p.name }
            BranchModel(it.name, if (it.previewValue == "true") "false" else "true", emptyList(), emptyList(), emptyList(), removed)
        }
        BranchKind.BoolAdds -> bool?.let {
            BranchModel(it.name, if (it.previewValue == "true") "false" else "true", emptyList(), emptyList(), listOf(ParameterModel.StringParam("extra", "String", null, true, "\${extra}")), emptyList())
        }
        BranchKind.EnumAdds -> enum?.let {
            BranchModel(it.name, listOf("A", "B", "C").first { v -> v != it.previewValue }, emptyList(), emptyList(), listOf(ParameterModel.IntParam("variant", "Int", "2", false, "2")), emptyList())
        }
    }
}

/** The template [spec] describes, as katachi would list it. [id] tells it from the other templates of its role. */
internal fun dialogTemplateOf(spec: DialogTemplateSpec, id: String?): TemplateModel {
    val roleName = (if (spec.group.isEmpty()) "" else spec.group + ".") + "R${spec.roleKey}"
    val captures = spec.pattern.caps.map { capture(it.name, module = it.module) }
    val flawed = spec.flaw == Flaw.UnknownKind
    val parameters = spec.params.map(::parameterOf) + listOfNotNull(if (flawed) ParameterModel.UnknownParam("future", "Future", null, false, "", kindName = "FutureParameter") else null)
    val model = template(
        roleName = roleName,
        id = id,
        parameters = parameters,
        files = listOf(file(spec.pattern.text.substringAfterLast('/'), path = null, pattern = spec.pattern.text, captures = captures.map { it.name })),
        branches = listOfNotNull(branchOf(spec.branch, parameters)),
        captures = captures,
    )
    return if (spec.flaw == Flaw.PreviewFailed) model.copy(detail = null, summary = model.summary.copy(conflict = true)) else model
}

private fun definitionOf(index: Int, specs: List<DialogTemplateSpec>): DialogDefinition {
    val shared = specs.groupingBy { it.roleKey }.eachCount()
    val pool = specs.mapIndexed { i, spec -> dialogTemplateOf(spec, id = if (shared.getValue(spec.roleKey) > 1) "v$i" else null) }
    return DialogDefinition(moduleOf(index), pool, specs.toString())
}

private fun definitionArb(index: Int, sizes: IntRange): Arb<DialogDefinition> = arbitrary {
    val count = Arb.int(sizes).bind()
    val specs = mutableListOf<DialogTemplateSpec>()
    repeat(count) { specs += templateSpecArb(first = index == 0 && specs.isEmpty(), previous = specs).bind() }
    definitionOf(index, specs)
}

/** One or two definitions of one to five templates (a definition with one template is the select box's smallest). */
internal val syntheticWorldArb: Arb<DialogWorld> = arbitrary {
    val definitions = Arb.choose(3 to Arb.constant(1), 2 to Arb.constant(2)).bind()
    DialogWorld((0 until definitions).map { definitionArb(it, 1..5).bind() }, "synthetic")
}

/** The samples' `katachiInternalTemplatesJson`, each alone and two side by side (each under its own root). */
internal val realWorlds: List<DialogWorld> by lazy {
    fun definition(index: Int, fixture: String) = DialogDefinition(moduleOf(index), ContractFixtures.templates(fixture), fixture)
    listOf(
        DialogWorld(listOf(definition(0, "sample-jvm-with-captures")), "real"),
        DialogWorld(listOf(definition(0, "sample-android-with-captures")), "real"),
        DialogWorld(listOf(definition(0, "sample-kmp-with-captures")), "real"),
        DialogWorld(listOf(definition(0, "sample-kmp-with-captures"), definition(1, "sample-android-with-captures")), "real"),
        DialogWorld(listOf(definition(0, "sample-jvm"), definition(1, "sample-kmp")), "real"),
    )
}

internal val realWorldArb: Arb<DialogWorld> = Arb.element(realWorlds)

/** Where the dialog is opened from. The numbers pick among what the world holds, so that a shrunk value stays valid. */
internal data class OriginPick(
    /** A notification on a file (`true`) or the New menu on a directory. */
    val editor: Boolean,
    val template: Int,
    val values: Int,
    val depth: Int,
    val leaf: Int,
    val form: Int,
)

internal val originPickArb: Arb<OriginPick> = arbitrary {
    OriginPick(
        editor = Arb.int(0..1).bind() == 0,
        template = Arb.int(0..50).bind(),
        values = Arb.int(0..1000).bind(),
        depth = Arb.int(0..50).bind(),
        leaf = Arb.int(0..50).bind(),
        form = Arb.int(0..5).bind(),
    )
}

/** What is on disk at the target, as far as the existing-file notice tells. */
internal enum class DiskKind { Missing, EmptyFile, Provisional, Content }

internal sealed interface DialogOp {
    /** The template select box: the n-th candidate. */
    data class SelectTemplate(val pick: Int) : DialogOp

    /** The definition select box (only there with two or more definitions). */
    data class SelectDefinition(val pick: Int) : DialogOp

    data class Type(val field: Int, val text: String) : DialogOp

    data class Clear(val field: Int) : DialogOp

    /** The shared list changes: some templates go. */
    data class Shrink(val salt: Int) : DialogOp

    /** The shared list changes: every template is back, in the JSON's order. */
    data object Restore : DialogOp

    /** The shared list changes: each definition's order rotates. */
    data class Rotate(val by: Int) : DialogOp

    /** Something outside writes the target file (a VFS event follows). */
    data class ExternalWrite(val kind: DiskKind) : DialogOp

    /** The dialog is cancelled: [mode] 0 = at rest, 1 = right after an input, 2 = between the input and its check. */
    data class Cancel(val mode: Int) : DialogOp

    /** [Generate]: the ViewModel's request, when it can be pressed. */
    data object Generate : DialogOp
}

private val texts = listOf(
    "", " ", "Home", "profile", "日本語", "a/b", "a.b", "..", "abc", "007", "-0", "+7", "A", "B", "Z", "true", "false", "x".repeat(40), "Foo Bar",
)

internal val dialogOpArb: Arb<DialogOp> = Arb.choose(
    8 to Arb.int(0..30).map<Int, DialogOp> { DialogOp.SelectTemplate(it) },
    4 to Arb.int(0..5).map<Int, DialogOp> { DialogOp.SelectDefinition(it) },
    16 to arbitrary<DialogOp> { DialogOp.Type(Arb.int(0..12).bind(), Arb.element(texts).bind()) },
    3 to Arb.int(0..12).map<Int, DialogOp> { DialogOp.Clear(it) },
    3 to Arb.int(0..1000).map<Int, DialogOp> { DialogOp.Shrink(it) },
    2 to Arb.constant<DialogOp>(DialogOp.Restore),
    1 to Arb.int(1..4).map<Int, DialogOp> { DialogOp.Rotate(it) },
    5 to Arb.enum<DiskKind>().map<DiskKind, DialogOp> { DialogOp.ExternalWrite(it) },
    1 to Arb.int(0..2).map<Int, DialogOp> { DialogOp.Cancel(it) },
    4 to Arb.constant<DialogOp>(DialogOp.Generate),
)

internal data class DialogScenario(val world: DialogWorld, val origin: OriginPick, val ops: List<DialogOp>) {
    override fun toString(): String = "\n  world=$world\n  origin=$origin\n  ops=$ops"
}

internal fun dialogScenarioArb(worlds: Arb<DialogWorld>, opCount: IntRange): Arb<DialogScenario> = arbitrary {
    DialogScenario(worlds.bind(), originPickArb.bind(), Arb.list(dialogOpArb, opCount).bind())
}
