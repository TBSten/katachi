package me.tbsten.katachi.intellij.ide.definition.psi

import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtValueArgumentList

// The shapes of a katachi definition's source that the injection of Kotlin into a template and the
// completion of its names both read (ide-authoring design 3.2). They look at the PSI only, never at
// a resolved symbol: the module that holds the definition need not be indexed, analysed or even
// synced for them to answer, which is what lets both run in dumb mode and under K1 as under K2.
//
// The price of not resolving is that a function of another library called the same way matches too;
// the receiver of a `template` has to be one of katachi's file declarations, which is specific
// enough in practice.
//

private const val KT_FILE = "ktFile"
private const val KT_S_FILE = "ktsFile"
private const val FILE = "file"

/** Calls that keep the declaration they are called on a file declaration (`"X.kt".file().optional()`). */
private val FILE_DECLARATION_MODIFIERS = setOf("optional", "metadata")

/** The kind of file a katachi template writes, as far as the language of its content goes. */
internal enum class TemplateFileKind(
    /** The extension of the injected fragment's file: `kts` is what makes (K1) Kotlin read it as a script. */
    val extension: String,
) {
    Kotlin("kt"),
    KotlinScript("kts"),
}

/** The text of the callee of a call, `template` of `template { }`. */
internal fun KtCallExpression.calleeName(): String? = calleeExpression?.text

/** The call this lambda is an argument of: trailing (`template { }`) or in parentheses (`template(block = { })`). */
internal fun callOfLambda(lambda: KtLambdaExpression): KtCallExpression? =
    when (val parent = lambda.parent) {
        is KtLambdaArgument -> parent.parent as? KtCallExpression
        is KtValueArgument -> (parent.parent as? KtValueArgumentList)?.parent as? KtCallExpression
        else -> null
    }

/** The lambda of the nearest enclosing call named [callee], for `return@template`. */
internal fun enclosingLambdaOfCall(from: KtExpression, callee: String): KtLambdaExpression? =
    generateSequence(from.parent) { it.parent }
        .filterIsInstance<KtLambdaExpression>()
        .firstOrNull { callOfLambda(it)?.calleeName() == callee }

/** `"X".ktFile()`, `"X".ktsFile()`, or `"X.kt".file()` / `"X.kts".file()`: the file a template is attached to. */
internal fun fileKindOfDeclaration(receiver: KtExpression?): TemplateFileKind? {
    val declaration = receiver as? KtDotQualifiedExpression ?: return null
    val call = declaration.selectorExpression as? KtCallExpression ?: return null
    if (call.valueArguments.isNotEmpty() || call.lambdaArguments.isNotEmpty()) return null
    return when (call.calleeName()) {
        KT_FILE -> TemplateFileKind.Kotlin
        KT_S_FILE -> TemplateFileKind.KotlinScript
        FILE -> {
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

/**
 * Whether [receiver] declares a file of any kind: `"X".ktFile()`, `"X".ktsFile()`, `"name".file()` whatever its
 * ending, and any of them followed by `.optional()` / `.metadata { }`.
 */
internal fun isFileDeclaration(receiver: KtExpression?): Boolean {
    val declaration = receiver as? KtDotQualifiedExpression ?: return false
    val call = declaration.selectorExpression as? KtCallExpression ?: return false
    return when (call.calleeName()) {
        KT_FILE, KT_S_FILE, FILE -> call.valueArguments.isEmpty() && call.lambdaArguments.isEmpty()
        in FILE_DECLARATION_MODIFIERS -> isFileDeclaration(declaration.receiverExpression)
        else -> false
    }
}

/** The receiver of this `template` call when it is written `receiver.template(...)`, else `null`. */
internal fun KtCallExpression.templateReceiver(): KtExpression? =
    (parent as? KtDotQualifiedExpression)?.takeIf { it.selectorExpression == this }?.receiverExpression
