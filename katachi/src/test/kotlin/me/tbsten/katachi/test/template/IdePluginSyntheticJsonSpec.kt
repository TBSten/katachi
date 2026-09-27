package me.tbsten.katachi.test.template

import io.kotest.core.spec.style.FreeSpec
import java.io.File
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.processor.FakeArchitectureProcessContext
import me.tbsten.katachi.template.DescribeTemplates
import me.tbsten.katachi.template.DescribeTemplatesFormat
import me.tbsten.katachi.test.dsl.files.ForbiddenFileSystem

private const val UPDATE_ENV: String = "KATACHI_UPDATE_IDE_PLUGIN_FIXTURES"

/** Relative to `katachi/`, the working directory of `:katachi:test`. */
private const val FIXTURE_DIRECTORY: String = "../katachi-intellij-plugin/src/test/resources/contract/json"

private const val UPDATE_COMMAND: String =
    "$UPDATE_ENV=true ./gradlew :katachi:test --tests '*IdePluginSyntheticJsonSpec*' --rerun"

private fun Architecture.templateDescriptionJson(): String = withTempProject { root ->
    val output = File(root, "templateDescription.json")
    val context = FakeArchitectureProcessContext(
        architecture = this,
        args = DescribeTemplates.Args(format = DescribeTemplatesFormat.Json, output = output.path),
        fileSystem = ForbiddenFileSystem,
    )
    DescribeTemplates.process(context).getOrThrow()
    output.readText()
}

private fun fixtureDirectory(): File {
    val directory = File(FIXTURE_DIRECTORY)
    if (!directory.isDirectory) {
        throw AssertionError(
            "The IDE plugin's fixture directory was not found at ${directory.absoluteFile.normalize()}.\n" +
                "This spec expects to run with katachi/ as its working directory, next to katachi-intellij-plugin/.",
        )
    }
    return directory
}

/** Compares [json] with the checked-in fixture, or writes it when [UPDATE_ENV] is `true`. */
private fun checkFixture(fileName: String, json: String) {
    val fixture = File(fixtureDirectory(), fileName)
    if (System.getenv(UPDATE_ENV) == "true") {
        fixture.writeText(json)
        return
    }
    val checkedIn = if (fixture.isFile) fixture.readText() else null
    if (checkedIn != json) {
        val state = if (checkedIn == null) "is missing" else "is out of date"
        throw AssertionError(
            "The IDE plugin's fixture ${fixture.absoluteFile.normalize()} $state: it no longer matches " +
                "the JSON DescribeTemplates writes for it.\n" +
                "If the change to the JSON is intended, update the fixtures from the repository root with\n" +
                "  $UPDATE_COMMAND\n" +
                "and run the IDE plugin's tests against them.",
        )
    }
}

/**
 * The JSON fixtures the IDE plugin's tests read, generated here from synthetic definitions.
 *
 * Each `synthetic-*.json` under `katachi-intellij-plugin/src/test/resources/contract/json/` is
 * the `internalTemplatesJson` output of one definition module. A change to that JSON fails this
 * spec until the fixtures are regenerated, and the regenerated fixtures then run through the
 * plugin's tests. What each fixture holds, role by role, is written next to its architecture in
 * `IdePluginSyntheticSpecSupport.kt`.
 */
class IdePluginSyntheticJsonSpec : FreeSpec({
    listOf(
        "synthetic-structure.json" to ::syntheticStructureArchitecture,
        "synthetic-many.json" to ::syntheticManyArchitecture,
        "synthetic-second.json" to ::syntheticSecondArchitecture,
    ).forEach { (fileName, architecture) ->
        "$fileName がいまの DescribeTemplates の JSON と一字一句同じ" {
            checkFixture(fileName, architecture().templateDescriptionJson())
        }
    }
})
