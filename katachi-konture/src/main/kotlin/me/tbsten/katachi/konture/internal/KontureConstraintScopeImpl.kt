package me.tbsten.katachi.konture.internal

import io.github.baole.konture.ClassesRuleBuilder
import io.github.baole.konture.FilesRuleBuilder
import io.github.baole.konture.FunctionsRuleBuilder
import io.github.baole.konture.ProjectGraph
import io.github.baole.konture.PropertiesRuleBuilder
import io.github.baole.konture.core.model.ViolationReport
import me.tbsten.katachi.konture.KontureConstraintScope

/**
 * Which of the four builders a rule came from.
 *
 * Decides how a violation is read back: Konture reports a file and a property both as a
 * `CustomSubject`, so the subject's type alone cannot tell them apart.
 */
internal enum class SuiteKind(
    /** The word the report and the exceptions use for this kind. */
    val label: String,
) {
    Files("files"),
    Classes("classes"),
    Functions("functions"),
    Properties("properties"),
}

/**
 * One rule a block declared, held until the block has finished.
 *
 * Declaring and evaluating are kept apart so that "the block declared no rule" can be said
 * before any rule has run, and so that every rule runs under one `audited { }`.
 */
internal class Suite(
    val kind: SuiteKind,
    /** Runs Konture's `check()` for this rule. Only meaningful inside `audited { }`. */
    val check: () -> ViolationReport,
)

/**
 * The receiver a `konture { }` block actually runs against: four builders bound to one graph.
 *
 * Each entry point makes Konture's own builder, bound to [graph] rather than to Konture's
 * default one, calls `allowEmpty()` so that a `that()` selecting nothing is green (the layout
 * check is what says whether a file is missing), runs the block to let it spell the rule, and
 * stores the `check()` for later. Nothing is evaluated here.
 */
internal class KontureConstraintScopeImpl(private val graph: ProjectGraph) : KontureConstraintScope {
    private val collected = mutableListOf<Suite>()

    /** Every rule the block declared, in declaration order. Empty is refused, not answered. */
    val suites: List<Suite> get() = collected

    override fun files(block: FilesRuleBuilder.() -> Unit) {
        val builder = FilesRuleBuilder(graph).apply { allowEmpty() }
        builder.block()
        collected += Suite(SuiteKind.Files) { builder.check(graph) }
    }

    override fun classes(block: ClassesRuleBuilder.() -> Unit) {
        val builder = ClassesRuleBuilder(graph).apply { allowEmpty() }
        builder.block()
        collected += Suite(SuiteKind.Classes) { builder.check(graph) }
    }

    override fun functions(block: FunctionsRuleBuilder.() -> Unit) {
        val builder = FunctionsRuleBuilder(graph).apply { allowEmpty() }
        builder.block()
        collected += Suite(SuiteKind.Functions) { builder.check(graph) }
    }

    override fun properties(block: PropertiesRuleBuilder.() -> Unit) {
        val builder = PropertiesRuleBuilder(graph).apply { allowEmpty() }
        builder.block()
        collected += Suite(SuiteKind.Properties) { builder.check(graph) }
    }
}
