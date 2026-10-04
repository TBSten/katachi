package me.tbsten.katachi.konture.internal

import me.tbsten.katachi.dsl.FileConstraint
import me.tbsten.katachi.dsl.FileConstraintFailure
import me.tbsten.katachi.dsl.FileConstraintSubject
import me.tbsten.katachi.konture.KatachiKontureDefaultGraphException
import me.tbsten.katachi.konture.KatachiKontureDirectAssertionException
import me.tbsten.katachi.konture.KatachiKontureNoExpectationException
import me.tbsten.katachi.konture.KatachiKontureRuleRefusedException
import me.tbsten.katachi.konture.KontureConstraintScope

/** The JVM name of Konture's `object Konture`, whose `projectGraph` getter is the one way to its default graph. */
private const val KONTURE_OBJECT: String = "io.github.baole.konture.Konture"

/** The JVM name of that getter: `val projectGraph` compiles to `getProjectGraph()`. */
private const val PROJECT_GRAPH_GETTER: String = "getProjectGraph"

/** The package Konture's four rule builders live in; a class there named `...RuleBuilder` is one of them. */
private const val KONTURE_PACKAGE_PREFIX: String = "io.github.baole.konture."

/**
 * What `konture { }` stores, and what running it comes to.
 *
 * Four steps, each of them a way this backend could otherwise go quiet. The graph is built from
 * the covered files alone, so a rule cannot reach a file its layout does not cover. The block
 * is run to collect rules, not to evaluate them, so a block that declared nothing is refused
 * before anything is checked. Every rule is then evaluated in Konture's audit mode, so that
 * violations are read back instead of thrown as prose. And each violation has to land on a
 * covered file, or it is a bug rather than a report block.
 */
internal class KontureFileConstraint(
    /** The block as written. Run once per constraint per run, never at declaration time. */
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

    /**
     * Runs the block so that it declares its rules into [scope], and refuses a block that
     * declared none.
     *
     * What comes out of the block is classified once here. An `AssertionError` is Konture
     * asserting from inside the block — a suppressed shadow, a hand-written `check()`. An
     * `IllegalArgumentException` or `IllegalStateException` that went through
     * `Konture.projectGraph` is Konture reaching for the default graph it does not have; one
     * that did not — a `require` of the reader's own, Konture refusing a local class — is left
     * as it is. katachi's own exceptions are subclasses of those two and never carry that
     * frame, so they pass through untouched as well. Anything else is left to
     * `FileConstraintCheck`.
     */
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

    /**
     * What an `AssertionError` out of a rule's `check()` means.
     *
     * With `allowEmpty()` already called, the one assertion Konture itself makes before looking
     * at a file is "this rule has no `should()`", thrown from the builder's own `check`. Every
     * other `AssertionError` was thrown by code the rule ran on a declaration — a predicate
     * that asserted instead of answering — and must not be reported as a missing `should()`.
     */
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

/** The type name a message quotes for an exception that came out of the block. */
private fun Throwable.spelling(): String = this::class.simpleName ?: this::class.java.name

/**
 * This exception wrapped as [KatachiKontureDefaultGraphException] when it came out of
 * `Konture.projectGraph`, itself otherwise. `KontureAssumptionsSpec` pins the frame.
 */
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

/**
 * Whether this error was constructed inside `check()` of one of Konture's rule builders, which
 * is where Konture refuses a rule's shape. A predicate's assertion is constructed in the
 * predicate, so its first frame is the reader's code.
 */
private fun AssertionError.thrownByRuleBuilderCheck(): Boolean {
    val origin = stackTrace.firstOrNull() ?: return false
    return origin.className.startsWith(KONTURE_PACKAGE_PREFIX) &&
        origin.className.endsWith("RuleBuilder") &&
        origin.methodName == "check"
}
