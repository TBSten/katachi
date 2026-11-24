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
import me.tbsten.katachi.intellij.presentation.gutter.SourceHeader
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtEnumEntry
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtTypeAlias

internal data class SourceDeclarationPsi(
    val declaration: SourceDeclaration,
    val leaf: SmartPsiElementPointer<PsiElement>,
    /** The PSI of [SourceDeclaration.members], in the same order. */
    val members: List<SourceDeclarationPsi> = emptyList(),
) {
    /** This declaration and its members, in the order the ◆ are drawn. */
    fun withMembers(): List<SourceDeclarationPsi> = listOf(this) + members
}

/** The `package` line and the imports of a file, with the leaf each ◆ goes at (KATACHI-98). */
internal data class SourceHeaderPsi(val packageLine: SourceDeclarationPsi?, val imports: List<SourceDeclarationPsi>) {
    val header: SourceHeader get() = SourceHeader(packageLine?.declaration, imports.map { it.declaration })

    /** Every line, in the order the ◆ are drawn. */
    fun all(): List<SourceDeclarationPsi> = listOfNotNull(packageLine) + imports

    companion object {
        val NONE: SourceHeaderPsi = SourceHeaderPsi(null, emptyList())
    }
}

// Read from the PSI alone (no resolve, no index) so it works in dumb mode. Call under a read lock, from the background.
internal object SourceDeclarations {
    fun of(file: KtFile): List<SourceDeclarationPsi> =
        sdkCall("read the top-level declarations of ${file.name}") {
            CachedValuesManager.getCachedValue(file) { CachedValueProvider.Result.create(compute(file), file) }
        }.getOrDefault(emptyList())

    /** The `package` line (named by the package) and the imports (named by the path, a star import as `a.b.*`), each at its first leaf. */
    fun headerOf(file: KtFile): SourceHeaderPsi =
        sdkCall("read the package and imports of ${file.name}") {
            CachedValuesManager.getCachedValue(file) { CachedValueProvider.Result.create(computeHeader(file), file) }
        }.getOrDefault(SourceHeaderPsi.NONE)

    private fun computeHeader(file: KtFile): SourceHeaderPsi {
        if (file.isScript()) return SourceHeaderPsi.NONE
        val pointers = SmartPointerManager.getInstance(file.project)
        val text = file.viewProvider.contents
        fun lineOf(directive: PsiElement, kind: SourceDeclarationKind, name: String): SourceDeclarationPsi? {
            val leaf = firstLeafOf(directive) ?: return null
            val line = StringUtil.offsetToLineNumber(text, leaf.textRange.startOffset) + 1
            return SourceDeclarationPsi(SourceDeclaration(kind, name, line), pointers.createSmartPsiElementPointer(leaf, file))
        }
        val packageLine = file.packageDirective?.takeIf { it.textLength > 0 }?.let { lineOf(it, SourceDeclarationKind.Package, file.packageFqName.asString()) }
        val imports = file.importDirectives.mapNotNull { directive ->
            directive.importPath?.pathStr?.let { lineOf(directive, SourceDeclarationKind.Import, it) }
        }
        return SourceHeaderPsi(packageLine, imports)
    }

    private fun compute(file: KtFile): List<SourceDeclarationPsi> {
        if (file.isScript()) return emptyList()
        val pointers = SmartPointerManager.getInstance(file.project)
        val text = file.viewProvider.contents
        return file.declarations.mapNotNull { declaration -> psiOf(declaration, file, text, pointers, withMembers = true) }
    }

    private fun psiOf(
        declaration: KtDeclaration,
        file: KtFile,
        text: CharSequence,
        pointers: SmartPointerManager,
        withMembers: Boolean,
    ): SourceDeclarationPsi? {
        val kind = kindOf(declaration) ?: return null
        val named = declaration as? KtNamedDeclaration ?: return null
        val name = named.name ?: return null
        val leaf = named.nameIdentifier ?: return null
        val line = StringUtil.offsetToLineNumber(text, startOffsetOf(declaration)) + 1
        val members = if (withMembers && declaration is KtClassOrObject) {
            // A property of the primary constructor (`class A(val b: Int)`) is a member, before what is in the body, as katachi reads them.
            val written = declaration.primaryConstructorParameters.filter { it.hasValOrVar() } + declaration.declarations
            written.mapNotNull { member -> psiOf(member, file, text, pointers, withMembers = false) }
        } else {
            emptyList()
        }
        return SourceDeclarationPsi(
            SourceDeclaration(kind, name, line, members.map { it.declaration }),
            pointers.createSmartPsiElementPointer(leaf, file),
            members,
        )
    }

    private fun kindOf(declaration: KtDeclaration): SourceDeclarationKind? = when (declaration) {
        is KtParameter -> if (declaration.isMutable) SourceDeclarationKind.Var else SourceDeclarationKind.Val
        is KtEnumEntry -> SourceDeclarationKind.Entry
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
