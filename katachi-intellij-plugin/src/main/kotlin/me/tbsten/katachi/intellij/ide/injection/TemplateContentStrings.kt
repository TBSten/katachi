package me.tbsten.katachi.intellij.ide.injection

import me.tbsten.katachi.intellij.ide.definition.psi.TemplateFileKind
import me.tbsten.katachi.intellij.ide.definition.psi.callOfLambda
import me.tbsten.katachi.intellij.ide.definition.psi.calleeName
import me.tbsten.katachi.intellij.ide.definition.psi.enclosingLambdaOfCall
import me.tbsten.katachi.intellij.ide.definition.psi.fileKindOfDeclaration
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtContainerNodeForControlStructureBody
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtWhenEntry

// Matches on the shape of the source only (never resolved symbols) so it runs in dumb mode; a `template` of another library called the same way matches too.
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

private const val TEMPLATE_FUNCTION = "template"

private val STRING_TRANSFORMS = setOf("trimIndent", "trimMargin", "trim", "trimStart", "trimEnd", "prependIndent", "replaceIndent")
