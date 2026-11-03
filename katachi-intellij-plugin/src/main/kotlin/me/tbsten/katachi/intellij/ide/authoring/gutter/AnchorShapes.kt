package me.tbsten.katachi.intellij.ide.authoring.gutter

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import me.tbsten.katachi.intellij.ide.definition.psi.calleeName
import me.tbsten.katachi.intellij.ide.definition.psi.isFileDeclaration
import me.tbsten.katachi.intellij.ide.definition.psi.templateReceiver
import me.tbsten.katachi.intellij.presentation.authoring.AnchorKind
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

internal data class AnchorShape(val kind: AnchorKind, val name: String?, val call: KtCallExpression, val leaf: PsiElement)

internal object AnchorShapes {
    fun of(call: KtCallExpression): AnchorShape? {
        val qualified = call.parent as? KtDotQualifiedExpression ?: return null
        if (qualified.selectorExpression !== call) return null
        return when (call.calleeName()) {
            ROLE -> role(call, qualified)
            TEMPLATE -> template(call)
            KT_FILE, KT_S_FILE, FILE -> entry(call, qualified)
            else -> null
        }
    }

    private fun role(call: KtCallExpression, qualified: KtDotQualifiedExpression): AnchorShape? {
        if (call.lambdaArguments.isEmpty() && call.valueArguments.none { it.getArgumentExpression() is KtLambdaExpression }) return null
        val name = qualified.receiverExpression as? KtStringTemplateExpression ?: return null
        val leaf = firstLeaf(name) ?: return null
        return AnchorShape(AnchorKind.Role, plainName(name), call, leaf)
    }

    private fun entry(call: KtCallExpression, qualified: KtDotQualifiedExpression): AnchorShape? {
        if (call.valueArguments.isNotEmpty() || call.lambdaArguments.isNotEmpty()) return null
        val name = qualified.receiverExpression as? KtStringTemplateExpression ?: return null
        val leaf = firstLeaf(name) ?: return null
        return AnchorShape(AnchorKind.Entry, entryName(name, call.calleeName()), call, leaf)
    }

    private fun template(call: KtCallExpression): AnchorShape? {
        if (call.lambdaArguments.isEmpty()) return null
        val receiver = call.templateReceiver()
        if (!isFileDeclaration(receiver)) return null
        val declaration = baseDeclarationOf(receiver) ?: return null
        val fileCall = declaration.selectorExpression as? KtCallExpression ?: return null
        val name = declaration.receiverExpression as? KtStringTemplateExpression ?: return null
        val leaf = call.calleeExpression?.let(::firstLeaf) ?: return null
        return AnchorShape(AnchorKind.Template, entryName(name, fileCall.calleeName()), call, leaf)
    }

    private fun baseDeclarationOf(receiver: KtExpression?): KtDotQualifiedExpression? {
        var current = receiver as? KtDotQualifiedExpression ?: return null
        while (true) {
            val call = current.selectorExpression as? KtCallExpression ?: return null
            if (call.calleeName() in FILE_CALLS) return current
            current = current.receiverExpression as? KtDotQualifiedExpression ?: return null
        }
    }

    private fun plainName(string: KtStringTemplateExpression): String? =
        if (string.entries.all { it is KtLiteralStringTemplateEntry }) string.entries.joinToString("") { it.text } else null

    private fun entryName(string: KtStringTemplateExpression, fileCall: String?): String {
        val text = string.entries.joinToString("") { if (it is KtLiteralStringTemplateEntry) it.text else "*" }
        val ending = when (fileCall) {
            KT_FILE -> ".kt"
            KT_S_FILE -> ".kts"
            else -> ""
        }
        return (text + ending).substringAfterLast('/')
    }

    private fun firstLeaf(element: PsiElement): PsiElement? =
        generateSequence(PsiTreeUtil.firstChild(element)) { PsiTreeUtil.nextLeaf(it) }.firstOrNull { it.textLength > 0 }
            ?.takeIf { PsiTreeUtil.isAncestor(element, it, false) }

    private const val ROLE = "role"
    private const val TEMPLATE = "template"
    private const val KT_FILE = "ktFile"
    private const val KT_S_FILE = "ktsFile"
    private const val FILE = "file"
    private val FILE_CALLS = setOf(KT_FILE, KT_S_FILE, FILE)
}
