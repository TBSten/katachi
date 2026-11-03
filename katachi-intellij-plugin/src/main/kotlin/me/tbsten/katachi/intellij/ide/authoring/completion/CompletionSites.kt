package me.tbsten.katachi.intellij.ide.authoring.completion

import me.tbsten.katachi.intellij.ide.definition.psi.callOfLambda
import me.tbsten.katachi.intellij.ide.definition.psi.calleeName
import me.tbsten.katachi.intellij.ide.definition.psi.isFileDeclaration
import me.tbsten.katachi.intellij.ide.definition.psi.templateReceiver
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtValueArgumentList

internal sealed interface CompletionSite {
    data class CaptureValue(val templateLine: Int) : CompletionSite

    data object TemplateSpec : CompletionSite

    data class ArgName(val spec: String) : CompletionSite
}

internal fun KtStringTemplateExpression.completionSite(): CompletionSite? {
    if (plainText() == null) return null
    val call = firstArgumentCall() ?: return null
    return when (call.calleeName()) {
        CAPTURE_VALUE -> templateCallAround(call)?.let { CompletionSite.CaptureValue(lineOf(it)) }
        TEMPLATE -> CompletionSite.TemplateSpec.takeIf { call.isDirectlyInTemplateSet() }
        ARG -> memberSpecAround(call)?.let { CompletionSite.ArgName(it) }
        else -> null
    }
}

internal fun KtStringTemplateExpression.plainText(): String? {
    if (text.startsWith("\"\"\"")) return null
    if (entries.any { it !is KtLiteralStringTemplateEntry }) return null
    return entries.joinToString("") { it.text }
}

private fun KtStringTemplateExpression.firstArgumentCall(): KtCallExpression? {
    val argument = parent as? KtValueArgument ?: return null
    val list = argument.parent as? KtValueArgumentList ?: return null
    if (list.arguments.firstOrNull() != argument) return null
    return list.parent as? KtCallExpression
}

private fun templateCallAround(from: KtCallExpression): KtCallExpression? =
    lambdasAround(from).mapNotNull { callOfLambda(it) }
        .firstOrNull { it.calleeName() == TEMPLATE && isFileDeclaration(it.templateReceiver()) }

private fun memberSpecAround(from: KtCallExpression): String? =
    lambdasAround(from).mapNotNull { callOfLambda(it) }
        .firstOrNull { it.calleeName() == TEMPLATE && it.isDirectlyInTemplateSet() }
        ?.let { member -> (member.valueArguments.firstOrNull()?.getArgumentExpression() as? KtStringTemplateExpression)?.plainText() }

private fun lambdasAround(from: KtCallExpression): Sequence<KtLambdaExpression> =
    generateSequence(from.parent) { it.parent }.filterIsInstance<KtLambdaExpression>()

private fun KtCallExpression.isDirectlyInTemplateSet(): Boolean {
    val block = parent as? KtBlockExpression ?: return false
    val lambda = (block.parent as? KtFunctionLiteral)?.parent as? KtLambdaExpression ?: return false
    val setCall = callOfLambda(lambda)?.takeIf { it.calleeName() == TEMPLATE_SET } ?: return false
    val qualified = setCall.parent as? KtDotQualifiedExpression ?: return false
    return qualified.selectorExpression == setCall && qualified.receiverExpression is KtStringTemplateExpression
}

private fun lineOf(call: KtCallExpression): Int {
    val offset = (call.calleeExpression ?: call).textRange.startOffset
    return call.containingFile.text.take(offset).count { it == '\n' } + 1
}

private const val CAPTURE_VALUE = "captureValue"
private const val TEMPLATE = "template"
private const val TEMPLATE_SET = "templateSet"
private const val ARG = "arg"
