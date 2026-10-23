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

/**
 * What `ktFileContent { }` stores: the block as written, run once per covered `.kt` file when a
 * check that evaluates constraints runs.
 *
 * Before any file is read, a run starts by refusing a second `ktFileContent { }` of the same role,
 * then runs the block once with stand-ins to refuse a `captureValue` name the layout does not
 * capture. Only then is each file's own run made and judged.
 *
 * It also explains itself to the IDE plugin ([DeclarationExplaining]): the same steps, keeping how
 * each top-level declaration was decided instead of what was wrong.
 */
internal class KtFileContentConstraint(
    private val block: KtFileContentScope.() -> Unit,
    /** Where `ktFileContent { }` was called. */
    val declaredAt: DeclarationSite,
) : FileConstraint, DeclarationExplaining {
    private val patterns = PatternCache()

    /** Runs the block once with stand-in values while the definition is read. See [validateAtDeclaration]. */
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

    /**
     * Each covered `.kt` file's top-level declarations, decided as [evaluate] decides them: the
     * same claim on the role, the same stand-in run, and per file the same rules and judging.
     */
    override fun explainDeclarations(subject: FileConstraintSubject): List<FileDeclarationsExplanation> {
        registerOnce(subject)
        evaluateWithStandIns(block, placeholderInput(subject, declaredAt, patterns), declaredAt)
        return explainKtFiles(subject) { file ->
            evaluateRules(block, inputFor(subject, file, declaredAt, patterns), declaredAt)
        }
    }

    /** The `## Kotlin file content` section of the role's page, worked out for its example file. */
    override fun documentSection(context: FileConstraintDocumentContext): String? {
        DocumentedBlocks.claim(context.role, this)
        return ktFileContentSection(block, context, declaredAt, patterns)
    }

    /**
     * Claims [subject]'s role for this block, refusing a block written somewhere else for the same
     * role.
     *
     * "Somewhere else" is a different call site, not a different instance: the block of a wildcard
     * module key (`":feature:*"`) is run once per module it resolves to, so the one
     * `ktFileContent { }` written there arrives as one instance per module, each about that
     * module's files, and is still one block.
     */
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

/** The memo key of a role's registry. A `Role` is equal only to itself, so one key per role. */
private data class KtFileContentRegistryKey(val role: Role)

/** The first `ktFileContent { }` block that ran for one role in one check. */
internal class KtFileContentRegistry {
    private val first = AtomicReference<KtFileContentConstraint?>(null)

    /** [constraint] when it is the first to ask, the one that was first otherwise. */
    fun claim(constraint: KtFileContentConstraint): KtFileContentConstraint =
        first.compareAndExchange(null, constraint) ?: constraint

    override fun toString(): String = "KtFileContentRegistry(${first.get()})"
}

/**
 * The first `ktFileContent { }` block whose section was written for each role, so that a role's
 * page refuses a second one the way the check does.
 *
 * The page generator hands a constraint no memo shared between a role's constraints, as a check
 * does ([FileConstraintSubject.memo]), so this is kept here, by role. A role is equal only to itself,
 * and is let go with its definition. Only the place is kept, never the block, so that a value does
 * not reach its key.
 * TODO: take it from the document context once that carries a role-wide memo, and drop this table.
 */
private object DocumentedBlocks {
    private val first = WeakHashMap<Role, DeclarationSite>()

    /**
     * @throws KatachiKtFileContentDuplicateException when [role] already has a block written at
     *   another place than [constraint].
     */
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
