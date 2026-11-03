package me.tbsten.katachi.intellij.ide.gutter

import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiTreeUtil
import me.tbsten.katachi.intellij.ide.sdkCall
import me.tbsten.katachi.intellij.model.SourceDeclarationKind
import me.tbsten.katachi.intellij.presentation.gutter.SourceDeclaration
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtTypeAlias

internal data class SourceDeclarationPsi(val declaration: SourceDeclaration, val leaf: SmartPsiElementPointer<PsiElement>)

// Read from the PSI alone (no resolve, no index) so it works in dumb mode. Call under a read lock, from the background.
internal object SourceDeclarations {
    fun of(file: KtFile): List<SourceDeclarationPsi> =
        sdkCall("read the top-level declarations of ${file.name}") {
            CachedValuesManager.getCachedValue(file) { CachedValueProvider.Result.create(compute(file), file) }
        }.getOrDefault(emptyList())

    private fun compute(file: KtFile): List<SourceDeclarationPsi> {
        if (file.isScript()) return emptyList()
        val pointers = SmartPointerManager.getInstance(file.project)
        val text = file.viewProvider.contents
        return file.declarations.mapNotNull { declaration ->
            val kind = kindOf(declaration) ?: return@mapNotNull null
            val named = declaration as? KtNamedDeclaration ?: return@mapNotNull null
            val name = named.name ?: return@mapNotNull null
            val leaf = named.nameIdentifier ?: return@mapNotNull null
            val line = StringUtil.offsetToLineNumber(text, startOffsetOf(declaration)) + 1
            SourceDeclarationPsi(SourceDeclaration(kind, name, line), pointers.createSmartPsiElementPointer(leaf, file))
        }
    }

    private fun kindOf(declaration: KtDeclaration): SourceDeclarationKind? = when (declaration) {
        is KtClass -> if (declaration.isInterface()) SourceDeclarationKind.Interface else SourceDeclarationKind.Class
        is KtObjectDeclaration -> if (declaration.isObjectLiteral()) null else SourceDeclarationKind.Object
        is KtNamedFunction -> SourceDeclarationKind.Fun
        is KtProperty -> if (declaration.isVar) SourceDeclarationKind.Var else SourceDeclarationKind.Val
        is KtTypeAlias -> SourceDeclarationKind.TypeAlias
        else -> null
    }

    fun startOffsetOf(declaration: KtDeclaration): Int =
        generateSequence(declaration.firstChild) { it.nextSibling }
            .firstOrNull { it !is PsiComment && it !is PsiWhiteSpace }
            ?.textRange?.startOffset
            ?: declaration.textRange.startOffset

    fun firstLeafOf(element: PsiElement): PsiElement? =
        generateSequence(PsiTreeUtil.firstChild(element)) { PsiTreeUtil.nextLeaf(it) }
            .firstOrNull { it !is PsiWhiteSpace && it.textLength > 0 && it.text.isNotBlank() }
}
