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
    // Konture's own `allowEmpty()` on the same builder, for telling its two refusals apart after the fact.
    val allowEmpty: () -> Unit,
)

// No `allowEmpty()` of katachi's: a rule that selects nothing is refused as Konture refuses it, unless the reader wrote one.
internal class KontureConstraintScopeImpl(private val graph: ProjectGraph) : KontureConstraintScope {
    private val collected = mutableListOf<Suite>()

    val suites: List<Suite> get() = collected

    override fun files(block: FilesRuleBuilder.() -> Unit) {
        val builder = FilesRuleBuilder(graph)
        builder.block()
        collected += Suite(SuiteKind.Files, check = { builder.check(graph) }, allowEmpty = { builder.allowEmpty() })
    }

    override fun classes(block: ClassesRuleBuilder.() -> Unit) {
        val builder = ClassesRuleBuilder(graph)
        builder.block()
        collected += Suite(SuiteKind.Classes, check = { builder.check(graph) }, allowEmpty = { builder.allowEmpty() })
    }

    override fun functions(block: FunctionsRuleBuilder.() -> Unit) {
        val builder = FunctionsRuleBuilder(graph)
        builder.block()
        collected += Suite(SuiteKind.Functions, check = { builder.check(graph) }, allowEmpty = { builder.allowEmpty() })
    }

    override fun properties(block: PropertiesRuleBuilder.() -> Unit) {
        val builder = PropertiesRuleBuilder(graph)
        builder.block()
        collected += Suite(SuiteKind.Properties, check = { builder.check(graph) }, allowEmpty = { builder.allowEmpty() })
    }
}
