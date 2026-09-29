package me.tbsten.katachi.intellij.ide.injection

import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtContainerNodeForControlStructureBody
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.kotlin.psi.KtWhenEntry

/** The kind of file a katachi template writes, as far as the language of its content goes. */
internal enum class TemplateFileKind(
    /** The extension of the injected fragment's file: `kts` is what makes (K1) Kotlin read it as a script. */
    val extension: String,
) {
    Kotlin("kt"),
    KotlinScript("kts"),
}

/**
 * What kind of file this string literal is the content of, when it is (part of) the value a katachi
 * `.template { }` block returns, and `null` for every other string.
 *
 * The match is on the shape of the source only, never on resolved symbols: the architecture module
 * that holds the definition need not be indexed, analysed or even synced for its templates to be
 * highlighted, which is what lets the injector run in dumb mode. The price is that a `template` of
 * another library called the same way would be matched too; the receiver has to be one of katachi's
 * file declarations (`.ktFile()`, `.ktsFile()`, or `.file()` on a name ending in `.kt` / `.kts`),
 * which is specific enough in practice.
 *
 * ## Example 1: the strings this recognises
 * ```kt
 * "*UseCase".ktFile().template {
 *     val name by stringParameter()
 *     val unrelated = "not the content"          // null: not what the block returns
 *     """
 *         interface ${name}UseCase                // Kotlin
 *     """.trimIndent() + "\n"                     // both literals: Kotlin
 * }
 * "build.gradle".ktsFile().template { "plugins { }" } // KotlinScript
 * ```
 */
internal fun KtStringTemplateExpression.templateFileKind(): TemplateFileKind? {
    val value = climbToValue(this)
    val lambda = when (val parent = value.parent) {
        is KtBlockExpression ->
            (parent.parent as? KtFunctionLiteral)?.takeIf { parent.statements.lastOrNull() == value }?.parent as? KtLambdaExpression
        is KtReturnExpression ->
            parent.getLabelName()?.takeIf { it == TEMPLATE_FUNCTION }?.let { enclosingLambdaOfCall(parent, TEMPLATE_FUNCTION) }
        else -> null
    } ?: return null
    val call = callOfLambda(lambda)?.takeIf { it.calleeName() == TEMPLATE_FUNCTION } ?: return null
    val qualified = (call.parent as? KtDotQualifiedExpression)?.takeIf { it.selectorExpression == call } ?: return null
    return fileKindOfDeclaration(qualified.receiverExpression)
}

/**
 * The outermost expression whose value still is this string's value (or contains it verbatim):
 * through parentheses, `+` concatenation, `trimIndent()` and the like, and the branches of `if` /
 * `when`.
 */
private fun climbToValue(start: KtExpression): KtExpression {
    var current: KtExpression = start
    while (true) {
        current = when (val parent = current.parent) {
            is KtParenthesizedExpression -> parent
            is KtBinaryExpression -> parent.takeIf { it.operationToken == KtTokens.PLUS } ?: return current
            is KtDotQualifiedExpression -> parent.takeIf {
                it.receiverExpression == current && (it.selectorExpression as? KtCallExpression)?.calleeName() in STRING_TRANSFORMS
            } ?: return current
            is KtContainerNodeForControlStructureBody -> parent.parent as? KtIfExpression ?: return current
            is KtWhenEntry -> parent.takeIf { it.expression == current }?.parent as? KtExpression ?: return current
            is KtBlockExpression -> {
                // A block that is an `if` / `when` branch has the value of its last statement.
                val isBranch = parent.parent is KtContainerNodeForControlStructureBody || parent.parent is KtWhenEntry
                if (isBranch && parent.statements.lastOrNull() == current) parent else return current
            }
            else -> return current
        }
    }
}

/** The lambda of the nearest enclosing call named [callee], for `return@template`. */
private fun enclosingLambdaOfCall(from: KtExpression, callee: String): KtLambdaExpression? =
    generateSequence(from.parent) { it.parent }
        .filterIsInstance<KtLambdaExpression>()
        .firstOrNull { callOfLambda(it)?.calleeName() == callee }

/** The call this lambda is an argument of: trailing (`template { }`) or in parentheses (`template(block = { })`). */
private fun callOfLambda(lambda: KtLambdaExpression): KtCallExpression? =
    when (val parent = lambda.parent) {
        is KtLambdaArgument -> parent.parent as? KtCallExpression
        is KtValueArgument -> (parent.parent as? KtValueArgumentList)?.parent as? KtCallExpression
        else -> null
    }

/** `"X".ktFile()`, `"X".ktsFile()`, or `"X.kt".file()` / `"X.kts".file()`: the file a template is attached to. */
private fun fileKindOfDeclaration(receiver: KtExpression?): TemplateFileKind? {
    val declaration = receiver as? KtDotQualifiedExpression ?: return null
    val call = declaration.selectorExpression as? KtCallExpression ?: return null
    if (call.valueArguments.isNotEmpty() || call.lambdaArguments.isNotEmpty()) return null
    return when (call.calleeName()) {
        "ktFile" -> TemplateFileKind.Kotlin
        "ktsFile" -> TemplateFileKind.KotlinScript
        "file" -> {
            // The name's own ending; a name that ends in an interpolation (`"$name".file()`) says nothing.
            val name = declaration.receiverExpression as? KtStringTemplateExpression ?: return null
            val ending = (name.entries.lastOrNull() as? KtLiteralStringTemplateEntry)?.text ?: return null
            when {
                ending.endsWith(".kts") -> TemplateFileKind.KotlinScript
                ending.endsWith(".kt") -> TemplateFileKind.Kotlin
                else -> null
            }
        }
        else -> null
    }
}

private fun KtCallExpression.calleeName(): String? = calleeExpression?.text

private const val TEMPLATE_FUNCTION = "template"

/** Calls on a string that keep its text as Kotlin source: the indentation and margin helpers. */
private val STRING_TRANSFORMS = setOf("trimIndent", "trimMargin", "trim", "trimStart", "trimEnd", "prependIndent", "replaceIndent")
