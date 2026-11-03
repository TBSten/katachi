package me.tbsten.katachi.konture.internal

import io.github.baole.konture.ClassesRuleBuilder
import io.github.baole.konture.FilesRuleBuilder
import io.github.baole.konture.FunctionsRuleBuilder
import io.github.baole.konture.ProjectGraph
import io.github.baole.konture.PropertiesRuleBuilder
import io.github.baole.konture.core.model.ViolationReport
import me.tbsten.katachi.konture.KontureConstraintScope

// Konture reports a file and a property both as a `CustomSubject`, so the subject's type alone cannot tell the builders apart.
internal enum class SuiteKind(
    val label: String,
) {
    Files("files"),
    Classes("classes"),
    Functions("functions"),
    Properties("properties"),
}

// Declaring and evaluating are kept apart so an empty block is refused before any rule runs, and every rule runs under one `audited { }`.
internal class Suite(
    val kind: SuiteKind,
    // Only meaningful inside `audited { }`.
    val check: () -> ViolationReport,
)

// `allowEmpty()` is called so that a `that()` selecting nothing is green; the layout check is what says whether a file is missing.
internal class KontureConstraintScopeImpl(private val graph: ProjectGraph) : KontureConstraintScope {
    private val collected = mutableListOf<Suite>()

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
