package me.tbsten.katachi.konture.internal

import me.tbsten.katachi.dsl.FileConstraint
import me.tbsten.katachi.dsl.FileConstraintFailure
import me.tbsten.katachi.dsl.FileConstraintSubject
import me.tbsten.katachi.konture.KatachiKontureDefaultGraphException
import me.tbsten.katachi.konture.KatachiKontureDirectAssertionException
import me.tbsten.katachi.konture.KatachiKontureNoExpectationException
import me.tbsten.katachi.konture.KatachiKontureRuleRefusedException
import me.tbsten.katachi.konture.KontureConstraintScope

private const val KONTURE_OBJECT: String = "io.github.baole.konture.Konture"

private const val PROJECT_GRAPH_GETTER: String = "getProjectGraph"

private const val KONTURE_PACKAGE_PREFIX: String = "io.github.baole.konture."

// Rules are collected before any is evaluated, so a block that declared nothing is refused before anything is checked.
internal class KontureFileConstraint(
    private val block: KontureConstraintScope.() -> Unit,
) : FileConstraint {
    override fun evaluate(subject: FileConstraintSubject): List<FileConstraintFailure> {
        val graph = kontureGraphOf(subject)
        val scope = KontureConstraintScopeImpl(graph)
        val covered = KontureCoveredFiles(subject.projectRoot, subject.files)
        // The block runs under audit too: a `check()` written by hand inside `files { }` then
        // returns a report that is simply dropped, instead of throwing Konture's prose.
        audited { declare(scope, subject) }
        // Audit mode is entered a second time for the evaluation, so that a `Konture.reset()`
        // or a `Konture.failOnSeverity = ERROR` written in the block (members of the object,
        // which no shadow can reach) cannot turn the violations below back into a throw.
        return audited {
            val sites = LinkedHashSet<KontureViolationSite>()
            for (suite in scope.suites) {
                val report = try {
                    suite.check()
                } catch (cause: AssertionError) {
                    throw classifyEvaluationAssertion(cause, suite, subject)
                }
                for (violation in report.violations) {
                    // A `@Suppress("konture:...")` the reader wrote in the code. Konture kept the
                    // violation and marked it; katachi honours the mark.
                    if (violation.isSuppressed) continue
                    sites += siteOf(suite.kind, violation, covered)
                }
            }
            sites.map { it.toFileConstraintFailure() }
        }
    }

    // An AssertionError is Konture asserting inside the block; an IAE/ISE through `Konture.projectGraph` is Konture reaching for the default graph; anything else passes through.
    private fun declare(scope: KontureConstraintScopeImpl, subject: FileConstraintSubject) {
        try {
            scope.block()
        } catch (cause: AssertionError) {
            throw KatachiKontureDirectAssertionException(spelling = cause.spelling(), suite = null, cause = cause)
        } catch (cause: IllegalArgumentException) {
            throw cause.asDefaultGraphFailureOrSelf()
        } catch (cause: IllegalStateException) {
            throw cause.asDefaultGraphFailureOrSelf()
        }
        if (scope.suites.isEmpty()) {
            throw KatachiKontureNoExpectationException(
                role = subject.role.qualifiedName,
                constraintName = subject.name,
                declaredAt = subject.declaredAt,
            )
        }
    }

    // With `allowEmpty()` called, the only assertion Konture makes before looking at a file is "no `should()`", from the builder's own `check`; any other came from a predicate.
    private fun classifyEvaluationAssertion(
        cause: AssertionError,
        suite: Suite,
        subject: FileConstraintSubject,
    ): Throwable = if (cause.thrownByRuleBuilderCheck()) {
        KatachiKontureRuleRefusedException(
            role = subject.role.qualifiedName,
            constraintName = subject.name,
            declaredAt = subject.declaredAt,
            suite = suite.kind.label,
            cause = cause,
        )
    } else {
        KatachiKontureDirectAssertionException(spelling = cause.spelling(), suite = suite.kind.label, cause = cause)
    }

    override fun toString(): String = "KontureFileConstraint"
}

private fun Throwable.spelling(): String = this::class.simpleName ?: this::class.java.name

// `KontureAssumptionsSpec` pins the frame.
private fun RuntimeException.asDefaultGraphFailureOrSelf(): RuntimeException {
    val reachedDefaultGraph = stackTrace.any {
        it.className == KONTURE_OBJECT && it.methodName == PROJECT_GRAPH_GETTER
    }
    return if (reachedDefaultGraph) {
        KatachiKontureDefaultGraphException(spelling = spelling(), cause = this)
    } else {
        this
    }
}

// A predicate's assertion is constructed in the predicate, so its first frame is the reader's code.
private fun AssertionError.thrownByRuleBuilderCheck(): Boolean {
    val origin = stackTrace.firstOrNull() ?: return false
    return origin.className.startsWith(KONTURE_PACKAGE_PREFIX) &&
        origin.className.endsWith("RuleBuilder") &&
        origin.methodName == "check"
}
