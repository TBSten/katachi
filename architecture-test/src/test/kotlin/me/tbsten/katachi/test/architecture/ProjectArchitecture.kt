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

const val KATACHI_MAIN_PACKAGE: String = "me/tbsten/katachi"

const val KONSIST_MAIN_PACKAGE: String = "me/tbsten/katachi/konsist"

const val KONTURE_MAIN_PACKAGE: String = "me/tbsten/katachi/konture"

const val SCRIPT_MAIN_PACKAGE: String = "me/tbsten/katachi/script"

const val DOKKA_MAIN_PACKAGE: String = "me/tbsten/katachi/dokka"

const val KATACHI_TEST_PACKAGE: String = "me/tbsten/katachi/test"

const val KONSIST_TEST_PACKAGE: String = "me/tbsten/katachi/test/konsist"

const val KONTURE_TEST_PACKAGE: String = "me/tbsten/katachi/test/konture"

const val SCRIPT_TEST_PACKAGE: String = "me/tbsten/katachi/test/script"

const val ARCHITECTURE_TEST_PACKAGE: String = "me/tbsten/katachi/test/architecture"

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
