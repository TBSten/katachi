package me.tbsten.katachi.intellij.ide.authoring.gutter

import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import me.tbsten.katachi.intellij.ide.sdkCall
import me.tbsten.katachi.intellij.presentation.authoring.Anchor
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid

internal data class PsiAnchor(val anchor: Anchor, val leaf: SmartPsiElementPointer<PsiElement>)

internal object DefinitionAnchors {
    fun of(file: KtFile): List<PsiAnchor> =
        sdkCall("read the anchors of ${file.name}") {
            CachedValuesManager.getCachedValue(file) { CachedValueProvider.Result.create(compute(file), file) }
        }.getOrDefault(emptyList())

    private fun compute(file: KtFile): List<PsiAnchor> {
        val pointers = SmartPointerManager.getInstance(file.project)
        val text = file.viewProvider.contents
        val found = mutableListOf<PsiAnchor>()
        file.accept(object : KtTreeVisitorVoid() {
            override fun visitCallExpression(expression: KtCallExpression) {
                super.visitCallExpression(expression)
                val shape = AnchorShapes.of(expression) ?: return
                // The line is the call's, as the stack frame katachi read `declaredAt` from names it; the mark sits on the leaf.
                val offset = (expression.calleeExpression ?: expression).textRange.startOffset
                val line = StringUtil.offsetToLineNumber(text, offset) + 1
                found += PsiAnchor(Anchor(shape.kind, shape.name, line), pointers.createSmartPsiElementPointer(shape.leaf, file))
            }
        })
        return found
    }
}
