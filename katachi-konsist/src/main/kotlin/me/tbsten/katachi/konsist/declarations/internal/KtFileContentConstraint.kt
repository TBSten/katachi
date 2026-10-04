package me.tbsten.katachi.konsist.declarations.internal

import java.util.concurrent.atomic.AtomicReference
import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.FileConstraint
import me.tbsten.katachi.dsl.FileConstraintDocumentContext
import me.tbsten.katachi.dsl.FileConstraintFailure
import me.tbsten.katachi.dsl.FileConstraintSubject
import me.tbsten.katachi.dsl.Role
import me.tbsten.katachi.konsist.declarations.KatachiKtFileContentDuplicateException
import me.tbsten.katachi.konsist.declarations.KtFileContentScope

/**
 * What `ktFileContent { }` stores: the block as written, run once per covered `.kt` file when a
 * check that evaluates constraints runs.
 *
 * Before any file is read, a run starts by refusing a second `ktFileContent { }` of the same role,
 * then runs the block once with stand-ins to refuse a `captureValue` name the layout does not
 * capture. Only then is each file's own run made and judged.
 */
internal class KtFileContentConstraint(
    private val block: KtFileContentScope.() -> Unit,
    /** Where `ktFileContent { }` was called. */
    val declaredAt: DeclarationSite,
) : FileConstraint {
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
        return judgeKtFiles(subject, declaredAt) { file ->
            evaluateRules(block, inputFor(subject, file, declaredAt, patterns), declaredAt)
        }
    }

    /** The `## Kotlin file content` section of the role's page, worked out for its example file. */
    override fun documentSection(context: FileConstraintDocumentContext): String? =
        ktFileContentSection(block, context, declaredAt, patterns)

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
