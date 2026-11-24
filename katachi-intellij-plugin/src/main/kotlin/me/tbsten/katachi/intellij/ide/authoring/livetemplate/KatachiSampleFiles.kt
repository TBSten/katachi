package me.tbsten.katachi.intellij.ide.authoring.livetemplate

import me.tbsten.katachi.intellij.ide.sdkCall
import me.tbsten.katachi.intellij.presentation.authoring.SampleDeclaration
import me.tbsten.katachi.intellij.presentation.authoring.SampleFile
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtTypeAlias

// Read from the PSI alone (no resolve, no index), as SourceDeclarations does. Call under a read lock.
internal object KatachiSampleFiles {
    fun of(file: KtFile): SampleFile? = sdkCall("read ${file.name} for a live template") {
        if (file.isScript()) return@sdkCall null
        SampleFile(
            baseName = file.name.substringBefore('.'),
            packageName = file.packageFqName.asString(),
            imports = file.importDirectives.mapNotNull { it.importPath?.pathStr },
            declarations = file.declarations.mapNotNull(::declarationOf),
        )
    }.getOrNull()

    private fun declarationOf(declaration: KtDeclaration): SampleDeclaration? {
        val name = (declaration as? KtNamedDeclaration)?.name ?: return null
        // A `fun interface` is written with two kinds, which the draft does not.
        if (declaration.hasModifier(KtTokens.FUN_KEYWORD) && declaration is KtClass) return null
        val kind = when (declaration) {
            is KtClass -> if (declaration.isInterface()) "interface" else "class"
            is KtObjectDeclaration -> if (declaration.isObjectLiteral()) return null else "object"
            is KtNamedFunction -> "fun"
            is KtProperty -> if (declaration.isVar) "var" else "val"
            is KtTypeAlias -> "typealias"
            else -> return null
        }
        val modifiers = KtTokens.MODIFIER_KEYWORDS_ARRAY.filter { declaration.hasModifier(it) }.map { it.value }
        return SampleDeclaration(modifiers, kind, name, (declaration as? KtClassOrObject)?.hasMembers() == true)
    }

    /** A body with a declaration of its own, or a `val` / `var` of the primary constructor: what a `{ }` after the line would have to allow. */
    private fun KtClassOrObject.hasMembers(): Boolean =
        body?.declarations.orEmpty().isNotEmpty() || primaryConstructorParameters.any { it.hasValOrVar() }
}
