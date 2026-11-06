package me.tbsten.katachi.test.architecture

import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.test.architecture.backend.backendGroup
import me.tbsten.katachi.test.architecture.cli.cliGroup
import me.tbsten.katachi.test.architecture.docs.docsGroup
import me.tbsten.katachi.test.architecture.gradle.gradleGroup
import me.tbsten.katachi.test.architecture.ideplugin.idePluginGroup
import me.tbsten.katachi.test.architecture.library.libraryGroup
import me.tbsten.katachi.test.architecture.plugin.pluginGroup
import me.tbsten.katachi.test.architecture.sample.sampleGroup
import me.tbsten.katachi.test.architecture.testing.testingGroup
import me.tbsten.katachi.test.architecture.tool.toolGroup

val projectArchitecture: Architecture = architecture {
    libraryGroup()
    backendGroup()
    pluginGroup()
    cliGroup()
    idePluginGroup()
    testingGroup()
    docsGroup()
    sampleGroup()
    gradleGroup()
    toolGroup()
}
