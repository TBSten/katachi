package me.tbsten.katachi.intellij.uitest.pbt

import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import me.tbsten.katachi.intellij.model.FilePreviewModel
import me.tbsten.katachi.intellij.model.ModuleChoiceModel
import me.tbsten.katachi.intellij.model.ModulePlacementModel
import me.tbsten.katachi.intellij.model.SetArgModel
import me.tbsten.katachi.intellij.model.TemplateModel
import me.tbsten.katachi.intellij.model.TemplateSetMemberModel
import me.tbsten.katachi.intellij.testing.setMember
import me.tbsten.katachi.intellij.testing.setTemplate

/*
 * Template sets for the generated definitions (template-set plan 3.6): a set has its own parameters (0-3, every kind a
 * template's has but the unknown one), 1-3 members whose files speak in those parameters (a file name too), sometimes one more
 * member below a module capture, and sometimes a preview that
 * failed (a row that cannot be checked). The first string parameter stands for a whole directory level of every member's
 * file, so that it is a field with directory candidates, as a capture is (template-set design 8.1).
 */

internal data class SetSpec(
    val group: String,
    val params: List<ParamSpec>,
    val members: Int,
    val previewFailed: Boolean,
    val title: TitleKind,
    /**
     * Whether the first string parameter stands only for the module key of one extra member (its file sits below a module
     * capture, `path == null`) and in no directory level of the others: the parameter is a module's name, not a directory.
     */
    val moduleKey: Boolean = false,
) {
    override fun toString(): String = buildString {
        append("S(${group.ifEmpty { "<root>" }} $params members=$members")
        if (previewFailed) append(" previewFailed")
        if (moduleKey) append(" moduleKey")
        if (title != TitleKind.None) append(" title=$title")
        append(")")
    }
}

private val SET_PARAM_NAMES = listOf("feature", "name", "flag", "kind", "count")

internal val setSpecArb: Arb<SetSpec> = arbitrary {
    SetSpec(
        group = Arb.element(listOf("", "", "a", "feature", "a.b")).bind(),
        params = Arb.list(
            arbitrary {
                ParamSpec(
                    Arb.element(SET_PARAM_NAMES).bind(),
                    Arb.element(ParamType.Str, ParamType.Str, ParamType.Bool, ParamType.Int, ParamType.Enum).bind(),
                    Arb.int(0..2).bind() == 0,
                )
            },
            0..3,
        ).bind().distinctBy { it.name },
        members = Arb.int(1..3).bind(),
        previewFailed = Arb.int(0..9).bind() == 0,
        title = Arb.enum<TitleKind>().bind(),
        moduleKey = Arb.int(0..3).bind() == 0,
    )
}

/** The module a [SetSpec.moduleKey] member's file lands in: the only one the key can name. */
internal const val SET_KEY_MODULE = "m0"

/** The row of the set [spec] describes, as the parser makes it from what katachi writes. */
internal fun setRowOf(index: Int, spec: SetSpec): TemplateModel {
    val specifier = if (spec.group.isEmpty()) "S$index" else "${spec.group}.S$index"
    val parameters = spec.params.map(::templateParameterOf)
    val strings = spec.params.filter { it.type == ParamType.Str }
    val key = strings.firstOrNull()?.name
    val inName = strings.getOrNull(1)?.name
    val directory = key?.takeUnless { spec.moduleKey }?.let { "\${$it}" }
    val base = "mod/src/${spec.group.ifEmpty { "root" }}"
    val members = (0 until spec.members).map { i ->
        val fileName = if (inName == null) "M$i.kt" else "M$i\${$inName}.kt"
        val path = listOfNotNull(base, directory, fileName).joinToString("/")
        // The member names its own capture `dir` and is handed the set's parameter (a renamed pass-through); its file speaks in the set's words.
        val args = key?.takeIf { directory != null }?.let { listOf(SetArgModel("dir", true, "\${$it}", it)) }.orEmpty()
        setMember("m.Member$i", "メンバ$i", path, args = args)
    } + listOfNotNull(key?.takeIf { spec.moduleKey }?.let { moduleKeyMemberOf(index, it) })
    val title = when (spec.title) {
        TitleKind.None -> specifier.substringAfterLast('.')
        TitleKind.Short -> "組$index"
        TitleKind.Long -> "とても長い組の題".repeat(6) + index
    }
    return setTemplate(specifier, members, parameters, title, unavailable = spec.previewFailed)
}

/** A member whose file sits below the module capture [key] (`path == null`), as katachi writes it for a set that passes the key on. */
private fun moduleKeyMemberOf(index: Int, key: String): TemplateSetMemberModel {
    val placement = ModulePlacementModel(
        ":feature:*",
        listOf(key),
        listOf(ModuleChoiceModel(listOf(SET_KEY_MODULE), ":feature:$SET_KEY_MODULE", "feature/$SET_KEY_MODULE", "feature/$SET_KEY_MODULE/src/Page.kt")),
    )
    val file = FilePreviewModel("feature/\${$key}/src/Page.kt", "Page.kt", null, listOf(key), emptyList(), "", placement)
    return TemplateSetMemberModel("m.ModuleKey$index", "モジュール$index", listOf(SetArgModel(key, true, "\${$key}", key)), listOf(file))
}
