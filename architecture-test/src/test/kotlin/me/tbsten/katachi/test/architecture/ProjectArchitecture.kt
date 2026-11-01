package me.tbsten.katachi.test.architecture

import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.test.architecture.backend.backendGroup
import me.tbsten.katachi.test.architecture.docs.docsGroup
import me.tbsten.katachi.test.architecture.gradle.gradleGroup
import me.tbsten.katachi.test.architecture.ideplugin.idePluginGroup
import me.tbsten.katachi.test.architecture.library.libraryGroup
import me.tbsten.katachi.test.architecture.plugin.pluginGroup
import me.tbsten.katachi.test.architecture.sample.sampleGroup
import me.tbsten.katachi.test.architecture.testing.testingGroup
import me.tbsten.katachi.test.architecture.tool.toolGroup

// Where each module keeps its production sources, below its own `src/main/kotlin`, and its test
// sources below `src/test/kotlin`. Written out rather than derived from the module name:
// `:katachi` is the root package itself and `:katachi-konsist` and `:katachi-konture` sit *inside* it, as
// `me.tbsten.katachi.konsist` and `me.tbsten.katachi.konture`, because the backend is one more package of the library rather than
// a library of its own.
//
// Test code lives one level deeper than production code, under `me.tbsten.katachi.test`, and that
// is load bearing rather than a convention.

const val KATACHI_MAIN_PACKAGE: String = "me/tbsten/katachi"

const val KONSIST_MAIN_PACKAGE: String = "me/tbsten/katachi/konsist"

const val KONTURE_MAIN_PACKAGE: String = "me/tbsten/katachi/konture"

const val DOKKA_MAIN_PACKAGE: String = "me/tbsten/katachi/dokka"

const val KATACHI_TEST_PACKAGE: String = "me/tbsten/katachi/test"

const val KONSIST_TEST_PACKAGE: String = "me/tbsten/katachi/test/konsist"

const val KONTURE_TEST_PACKAGE: String = "me/tbsten/katachi/test/konture"

const val DOKKA_TEST_PACKAGE: String = "me/tbsten/katachi/test/dokka"

const val ARCHITECTURE_TEST_PACKAGE: String = "me/tbsten/katachi/test/architecture"

val projectArchitecture: Architecture = architecture {
    libraryGroup()
    backendGroup()
    pluginGroup()
    idePluginGroup()
    testingGroup()
    docsGroup()
    sampleGroup()
    gradleGroup()
    toolGroup()
}
