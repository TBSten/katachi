package me.tbsten.katachi.intellij.ide.authoring.livetemplate

import com.intellij.codeInsight.template.Expression
import com.intellij.codeInsight.template.ExpressionContext
import com.intellij.codeInsight.template.Macro
import com.intellij.codeInsight.template.Result
import com.intellij.codeInsight.template.TextResult
import me.tbsten.katachi.intellij.ide.sdkCall
import me.tbsten.katachi.intellij.presentation.authoring.FileContentDraft

/**
 * The variables of the katachifilecontent and katachitemplate live templates that the loaded definition fills
 * (ide-live-template-from-definition). Each answers `null` when there is nothing to draft from, and the variable then takes the
 * fixed text of its `defaultValue` in katachi.xml, so a template expands the same as before in a project with no definition.
 */
internal abstract class KatachiDraftMacro(private val macroName: String) : Macro() {
    override fun getName(): String = macroName

    override fun getPresentableName(): String = "$macroName()"

    override fun calculateResult(params: Array<out Expression>, context: ExpressionContext): Result? =
        sdkCall("fill $macroName from the definition") { valueOf(context) }.getOrNull()?.let { TextResult(it) }

    protected abstract fun valueOf(context: ExpressionContext): String?
}

/** The `package("…")` pattern the role's files share. */
internal class KatachiDraftPackageMacro : KatachiDraftMacro("katachiDraftPackage") {
    override fun valueOf(context: ExpressionContext): String? = KatachiDraftSource.fileContent(context)?.packagePattern
}

/** The `import("…")` lines, each followed by a line break, and a blank line after them. */
internal class KatachiDraftImportsMacro : KatachiDraftMacro("katachiDraftImports") {
    override fun valueOf(context: ExpressionContext): String? =
        KatachiDraftSource.fileContent(context)?.importLines?.takeIf { it.isNotEmpty() }?.joinToString("\n", postfix = "\n\n")
}

/**
 * The declaration lines: what the files hold. With nothing known it is the opening of the one fixed line, which
 * [KatachiDraftTailMacro] closes: the cursor of the template ends between the two, inside the `{ }`. They are written here and not
 * as the `defaultValue` of the variables because a default is one line of an expression.
 */
internal class KatachiDraftDeclarationsMacro : KatachiDraftMacro("katachiDraftDeclarations") {
    override fun valueOf(context: ExpressionContext): String =
        KatachiDraftSource.fileContent(context)?.declarationLines?.takeIf { it.isNotEmpty() }?.joinToString("\n") ?: "required(`class`, fileName) {\n        "
}

/** What closes the fixed declaration line; nothing for a draft, whose lines close themselves. */
internal class KatachiDraftTailMacro : KatachiDraftMacro("katachiDraftTail") {
    override fun valueOf(context: ExpressionContext): String =
        if (KatachiDraftSource.fileContent(context)?.declarationLines.isNullOrEmpty()) "\n    }" else ""
}

/** The first capture of the role's template, which is what the `val` of the template function is named. */
internal class KatachiDraftCaptureMacro : KatachiDraftMacro("katachiDraftCapture") {
    override fun valueOf(context: ExpressionContext): String? = KatachiDraftSource.template(context)?.captureNames?.firstOrNull()
}

/** The `val` lines for the captures after the first. */
internal class KatachiDraftMoreCapturesMacro : KatachiDraftMacro("katachiDraftMoreCaptures") {
    override fun valueOf(context: ExpressionContext): String? =
        KatachiDraftSource.template(context)?.captureNames?.drop(1)?.takeIf { it.isNotEmpty() }
            ?.joinToString("") { "\n    val $it = captureValue(\"$it\")" }
}

/** The file's text for the body of the template. */
internal class KatachiDraftTemplateBodyMacro : KatachiDraftMacro("katachiDraftTemplateBody") {
    override fun valueOf(context: ExpressionContext): String? =
        KatachiDraftSource.template(context)?.body?.lines()?.joinToString("\n") { if (it.isEmpty()) it else "    $it" }
}
