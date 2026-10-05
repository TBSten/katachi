package me.tbsten.katachi.test.ide

import io.kotest.core.spec.style.FreeSpec
import java.io.File
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.files.KatachiFileSystem
import me.tbsten.katachi.test.dsl.files.fakeFileSystem
import me.tbsten.katachi.test.template.syntheticManyArchitecture
import me.tbsten.katachi.test.template.syntheticSecondArchitecture
import me.tbsten.katachi.test.template.syntheticStructureArchitecture

private const val UPDATE_ENV: String = "KATACHI_UPDATE_IDE_PLUGIN_FIXTURES"

/** Relative to `katachi/`, the working directory of `:katachi:test`. */
private const val FIXTURE_DIRECTORY: String = "../katachi-intellij-plugin/src/test/resources/contract/definition"

private const val UPDATE_COMMAND: String =
    "$UPDATE_ENV=true ./gradlew :katachi:test --tests '*IdePluginDefinitionFixtureSpec*' --rerun"

/**
 * The project the synthetic definitions are walked over: the modules `IdePluginSyntheticJsonSpec`
 * gives them (so `templates` is that spec's JSON as it is), and a file for some of the templated
 * entries plus one nothing declares, so `matchedFiles` holds something to match against.
 */
private fun syntheticDefinitionProject(): KatachiFileSystem = fakeFileSystem(workingDirectory = "/repo/architecture-test") {
    "/repo" {
        "gradlew"()
        "architecture-test/build.gradle.kts"()
        "feature/home" {
            "build.gradle.kts"()
            "src/main/kotlin/HomeScreen.kt"()
        }
        "feature/settings/build.gradle.kts"()
        "src/main/kotlin" {
            "allTypes" { "AllTypes.kt"() }
            "a" {
                "Shallow.kt"()
                "b" { "Middle.kt"() }
            }
            "counter" { "Counter.kt"() }
            "many" {
                "T000.kt"()
                "T123.kt"()
            }
            "secondRoot" { "SecondRoot.kt"() }
            "stray" { "Nobody.kt"() }
        }
    }
}

private fun fixtureDirectory(): File {
    val directory = File(FIXTURE_DIRECTORY)
    if (!directory.parentFile.isDirectory) {
        throw AssertionError(
            "The IDE plugin's contract directory was not found at ${directory.parentFile.absoluteFile.normalize()}.\n" +
                "This spec expects to run with katachi/ as its working directory, next to katachi-intellij-plugin/.",
        )
    }
    return directory
}

/** Compares [json] with the checked-in fixture, or writes it when [UPDATE_ENV] is `true`. */
private fun checkFixture(fileName: String, json: String) {
    val fixture = File(fixtureDirectory(), fileName)
    if (System.getenv(UPDATE_ENV) == "true") {
        fixture.parentFile.mkdirs()
        fixture.writeText(json)
        return
    }
    val checkedIn = if (fixture.isFile) fixture.readText() else null
    if (checkedIn != json) {
        val state = if (checkedIn == null) "is missing" else "is out of date"
        throw AssertionError(
            "The IDE plugin's fixture ${fixture.absoluteFile.normalize()} $state: it no longer matches " +
                "the JSON DescribeDefinition writes for it.\n" +
                "If the change to the JSON is intended, update the fixtures from the repository root with\n" +
                "  $UPDATE_COMMAND\n" +
                "and run the IDE plugin's tests against them.",
        )
    }
}

/**
 * The definition JSON fixtures the IDE plugin's tests read, generated here from the same synthetic
 * definitions as `IdePluginSyntheticJsonSpec`'s template fixtures.
 *
 * Each `synthetic-definition-*.json` under `katachi-intellij-plugin/src/test/resources/contract/definition/`
 * is the `internalDefinitionJson` output of one definition module, with `generatedAt` and
 * `katachiVersion` fixed so that a release does not change them. A change to that JSON fails this
 * spec until the fixtures are regenerated, and the regenerated fixtures then run through the
 * plugin's tests.
 */
class IdePluginDefinitionFixtureSpec : FreeSpec({
    listOf<Triple<String, () -> Architecture, () -> KatachiFileSystem>>(
        Triple("synthetic-definition-structure.json", ::syntheticStructureArchitecture, ::syntheticDefinitionProject),
        Triple("synthetic-definition-many.json", ::syntheticManyArchitecture, ::syntheticDefinitionProject),
        Triple("synthetic-definition-second.json", ::syntheticSecondArchitecture, ::syntheticDefinitionProject),
        // The two below carry constraints (custom, DirectOnly, anyFile, and fileDeclarations in every
        // verdict), which the three above do not: the plugin's parser reads their shape from here.
        Triple("synthetic-definition-basic.json", ::basicDefinition, ::basicProject),
        Triple("synthetic-definition-explained.json", ::explainedDefinition, ::explainedProject),
    ).forEach { (fileName, architecture, project) ->
        "$fileName がいまの DescribeDefinition の JSON と一字一句同じ" {
            checkFixture(fileName, architecture().definitionJsonText(project()))
        }
    }
})
