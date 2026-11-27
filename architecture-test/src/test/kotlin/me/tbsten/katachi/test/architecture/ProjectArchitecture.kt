package me.tbsten.katachi.test.architecture

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.konsist.definition.katachiDefinition
import me.tbsten.katachi.test.architecture.backend.backendGroup
import me.tbsten.katachi.test.architecture.cli.cliGroup
import me.tbsten.katachi.test.architecture.docs.docsGroup
import me.tbsten.katachi.test.architecture.gradle.gradleGroup
import me.tbsten.katachi.test.architecture.ideplugin.idePluginGroup
import me.tbsten.katachi.test.architecture.library.libraryGroup
import me.tbsten.katachi.test.architecture.plugin.pluginGroup
import me.tbsten.katachi.test.architecture.sample.sampleGroup
import me.tbsten.katachi.test.architecture.skill.skillGroup
import me.tbsten.katachi.test.architecture.testing.testingGroup
import me.tbsten.katachi.test.architecture.tool.toolGroup

@OptIn(ExperimentalKatachiApi::class)
val projectArchitecture: Architecture = architecture {
    katachiDefinition(
        rootPackage = "me.tbsten.katachi.test.architecture",
        // This module's tests are kotest specs (the Spec role), so there is no *Test.kt.
        architectureTest = { enabled = false },
        // What a role of one group may not reach: the roles of another group.
        role = { content { not.`import`("me.tbsten.katachi.test.architecture.*.*.**") } },
    )
    libraryGroup()
    backendGroup()
    pluginGroup()
    cliGroup()
    idePluginGroup()
    skillGroup()
    testingGroup()
    docsGroup()
    sampleGroup()
    gradleGroup()
    toolGroup()
}
