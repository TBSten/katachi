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

// PSI only, never a resolved symbol: runs in dumb mode and under K1 as under K2.
// A function of another library called the same way matches too; the receiver must be a katachi file declaration.

private const val KT_FILE = "ktFile"
private const val KT_S_FILE = "ktsFile"
private const val FILE = "file"

private val FILE_DECLARATION_MODIFIERS = setOf("optional", "metadata")

internal enum class TemplateFileKind(
    // `kts` is what makes K1 Kotlin read the fragment as a script.
    val extension: String,
) {
    Kotlin("kt"),
    KotlinScript("kts"),
}

internal fun KtCallExpression.calleeName(): String? = calleeExpression?.text

internal fun callOfLambda(lambda: KtLambdaExpression): KtCallExpression? =
    when (val parent = lambda.parent) {
        is KtLambdaArgument -> parent.parent as? KtCallExpression
        is KtValueArgument -> (parent.parent as? KtValueArgumentList)?.parent as? KtCallExpression
        else -> null
    }

internal fun enclosingLambdaOfCall(from: KtExpression, callee: String): KtLambdaExpression? =
    generateSequence(from.parent) { it.parent }
        .filterIsInstance<KtLambdaExpression>()
        .firstOrNull { callOfLambda(it)?.calleeName() == callee }

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

internal fun isFileDeclaration(receiver: KtExpression?): Boolean {
    val declaration = receiver as? KtDotQualifiedExpression ?: return false
    val call = declaration.selectorExpression as? KtCallExpression ?: return false
    return when (call.calleeName()) {
        KT_FILE, KT_S_FILE, FILE -> call.valueArguments.isEmpty() && call.lambdaArguments.isEmpty()
        in FILE_DECLARATION_MODIFIERS -> isFileDeclaration(declaration.receiverExpression)
        else -> false
    }
}

internal fun KtCallExpression.templateReceiver(): KtExpression? =
    (parent as? KtDotQualifiedExpression)?.takeIf { it.selectorExpression == this }?.receiverExpression
