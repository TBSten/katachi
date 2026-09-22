package me.tbsten.katachi.konsist

import com.lemonappdev.konsist.core.exception.KoException
import me.tbsten.katachi.dsl.ConstraintFailure
import me.tbsten.katachi.dsl.ConstraintSubject
import me.tbsten.katachi.dsl.FileSetConstraint

/**
 * What `konsist { }` stores, and what running it comes to.
 *
 * Three things happen here and each of them is a way this backend could otherwise go quiet.
 * The scope is built from the covered files alone, so a block cannot reach a file its layout
 * does not cover. The block is run with Konsist's own assertions turned into failures rather
 * than left to throw prose katachi could not report. And a block that asked for nothing is
 * refused, because "rejected nothing" and "was never asked" read identically in a report.
 */
internal class KonsistConstraint(
    /** The block as written. Run once per constraint per run, never at declaration time. */
    internal val block: KonsistScope.() -> Unit,
) : FileSetConstraint {
    override fun evaluate(subject: ConstraintSubject): List<ConstraintFailure> {
        val scope = KonsistScopeImpl(konsistScopeOf(subject))
        try {
            scope.block()
        } catch (cause: KoException) {
            // Konsist's own assertion went off — the shadow in `KonsistScope` was suppressed,
            // or Konsist threw from somewhere katachi does not shadow. Changing the type here
            // is what keeps the report's "How to fix" from telling the reader to file a
            // katachi bug for something they wrote.
            throw KatachiKonsistDirectAssertionException(
                spelling = cause::class.simpleName ?: cause::class.java.name,
                cause = cause,
            )
        }
        if (scope.expectations == 0) {
            throw KatachiKonsistNoExpectationException(
                role = subject.role.qualifiedName,
                constraintName = subject.name,
                declaredAt = subject.declaredAt,
            )
        }
        return scope.rejected.map { it.toConstraintFailure(subject.projectRoot) }
    }

    override fun toString(): String = "KonsistConstraint"
}
