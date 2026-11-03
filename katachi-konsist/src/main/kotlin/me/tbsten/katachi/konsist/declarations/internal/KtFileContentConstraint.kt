@file:OptIn(InternalKatachiApi::class)

package me.tbsten.katachi.konsist.declarations.internal

import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicReference
import me.tbsten.katachi.InternalKatachiApi
import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.FileConstraint
import me.tbsten.katachi.dsl.FileConstraintDocumentContext
import me.tbsten.katachi.dsl.FileConstraintFailure
import me.tbsten.katachi.dsl.FileConstraintSubject
import me.tbsten.katachi.dsl.Role
import me.tbsten.katachi.dsl.internal.DeclarationExplaining
import me.tbsten.katachi.dsl.internal.FileDeclarationsExplanation
import me.tbsten.katachi.konsist.declarations.KatachiKtFileContentDuplicateException
import me.tbsten.katachi.konsist.declarations.KtFileContentScope

// Order: refuse a second `ktFileContent { }` of the same role, then run once with stand-ins (refusing a `captureValue` name the layout does not capture),
// and only then run and judge each file.
internal class KtFileContentConstraint(
    private val block: KtFileContentScope.() -> Unit,
    val declaredAt: DeclarationSite,
) : FileConstraint, DeclarationExplaining {
    private val patterns = PatternCache()

    fun validateAtDeclaration() {
        validateAtDeclaration(block, declaredAt, patterns)
    }

    override fun evaluate(subject: FileConstraintSubject): List<FileConstraintFailure> {
        registerOnce(subject)
        // Stand-ins once more, now that the layout is known: a `captureValue` name it does not
        // capture is refused here, before any file is read.
        evaluateWithStandIns(block, placeholderInput(subject, declaredAt, patterns), declaredAt)
        return judgeKtFiles(subject) { file ->
            evaluateRules(block, inputFor(subject, file, declaredAt, patterns), declaredAt)
        }
    }

    override fun explainDeclarations(subject: FileConstraintSubject): List<FileDeclarationsExplanation> {
        registerOnce(subject)
        evaluateWithStandIns(block, placeholderInput(subject, declaredAt, patterns), declaredAt)
        return explainKtFiles(subject) { file ->
            evaluateRules(block, inputFor(subject, file, declaredAt, patterns), declaredAt)
        }
    }

    override fun documentSection(context: FileConstraintDocumentContext): String? {
        DocumentedBlocks.claim(context.role, this)
        return ktFileContentSection(block, context, declaredAt, patterns)
    }

    // "Somewhere else" is a different call site, not a different instance: the block of a wildcard module key runs once per module but is one block.
    private fun registerOnce(subject: FileConstraintSubject) {
        val registry = subject.memo(KtFileContentRegistryKey(subject.role), KtFileContentRegistry::class) {
            KtFileContentRegistry()
        }
        val first = registry.claim(this)
        if (first === this || first.declaredAt == declaredAt) return
        throw KatachiKtFileContentDuplicateException(
            role = subject.role.qualifiedName,
            firstDeclaredAt = first.declaredAt,
            declaredAt = declaredAt,
        )
    }

    override fun toString(): String = "KtFileContentConstraint($declaredAt)"
}

private data class KtFileContentRegistryKey(val role: Role)

internal class KtFileContentRegistry {
    private val first = AtomicReference<KtFileContentConstraint?>(null)

    fun claim(constraint: KtFileContentConstraint): KtFileContentConstraint =
        first.compareAndExchange(null, constraint) ?: constraint

    override fun toString(): String = "KtFileContentRegistry(${first.get()})"
}

// Only the place is kept, never the block, so that a value does not reach its key.
// TODO: take it from the document context once that carries a role-wide memo, and drop this table.
private object DocumentedBlocks {
    private val first = WeakHashMap<Role, DeclarationSite>()

    fun claim(role: Role, constraint: KtFileContentConstraint) {
        val earlier = synchronized(first) { first.getOrPut(role) { constraint.declaredAt } }
        if (earlier == constraint.declaredAt) return
        throw KatachiKtFileContentDuplicateException(
            role = role.qualifiedName,
            firstDeclaredAt = earlier,
            declaredAt = constraint.declaredAt,
        )
    }
}
