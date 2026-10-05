package me.tbsten.katachi.test.architecture.groups

import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.test.architecture.roles.agentRule
import me.tbsten.katachi.test.architecture.roles.benchmark
import me.tbsten.katachi.test.architecture.roles.ci
import me.tbsten.katachi.test.architecture.roles.dokkaPlugin
import me.tbsten.katachi.test.architecture.roles.git
import me.tbsten.katachi.test.architecture.roles.ideDictionary
import me.tbsten.katachi.test.architecture.roles.ideRunConfiguration

/**
 * The roles of the tooling around the project that is not the build itself.
 *
 * Like the `Gradle` group, these are checked but kept out of the generated documentation.
 *
 * `:tool:dokka` belongs here too: a Dokka plugin used only by this repository's own API
 * reference, never published and never depended on by the library. So does `:benchmark`, the
 * JMH benchmarks, for the same reason.
 */
fun DeclarationContainerScope.toolGroup() = "tool".group {
    documented = false
    title = "ツール設定"

    ci()
    agentRule()
    git()
    ideRunConfiguration()
    ideDictionary()
    dokkaPlugin()
    benchmark()
}
