package me.tbsten.katachi.test.template

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.io.File
import me.tbsten.katachi.check.internal.moduleIndex
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.ModuleResolver
import me.tbsten.katachi.dsl.files.FsPath
import me.tbsten.katachi.dsl.files.KatachiFileSystem
import me.tbsten.katachi.dsl.gradle.module
import me.tbsten.katachi.dsl.gradle.wildcard
import me.tbsten.katachi.dsl.pascalCase
import me.tbsten.katachi.dsl.template
import me.tbsten.katachi.processor.internal.FakeArchitectureProcessContext
import me.tbsten.katachi.processor.internal.process
import me.tbsten.katachi.template.DescribeTemplates
import me.tbsten.katachi.template.DescribeTemplatesFormat
import me.tbsten.katachi.template.internal.TemplateModulePlacement
import me.tbsten.katachi.template.internal.declaredTemplatesOf
import me.tbsten.katachi.template.internal.modulePlacementsOf
import me.tbsten.katachi.template.internal.templateFilesFor
import me.tbsten.katachi.test.check.architectureOf
import me.tbsten.katachi.test.dsl.files.ForbiddenFileSystem
import me.tbsten.katachi.test.dsl.files.fakeFileSystem

/** A project at `/repo` whose modules are `:architecture-test` and [features] below `feature/`. */
private fun projectWith(vararg features: String): KatachiFileSystem =
    fakeFileSystem(workingDirectory = "/repo/architecture-test") {
        "/repo" {
            "gradlew"()
            "architecture-test/build.gradle.kts"()
            features.forEach { "feature/$it/build.gradle.kts"() }
        }
    }

/**
 * `":feature:${capture("feature")}".module { }` with a file whose package directory and name are
 * built from the module's value (`wildcard("feature")`, `.pascalCase`), as sample/android's
 * FeatureComponent is: what the IDE cannot work out from the module's name alone.
 */
private fun featureComponent(resolver: ModuleResolver = ModuleResolver.Conventional): Architecture = architectureOf {
    moduleResolver = resolver
    "feature".group {
        "Component" {
            layout {
                ":feature:${capture("feature")}".module {
                    "src/main/kotlin/feature" / wildcard("feature") /
                        "${wildcard("feature").pascalCase}${capture("name")}.kt".file().template {
                            "package feature.${captureValue("feature")}"
                        }
                }
            }
        }
    }
}

private fun Architecture.placements(fileSystem: KatachiFileSystem): List<TemplateModulePlacement> =
    process(fileSystem) { context ->
        modulePlacementsOf(declaredTemplatesOf(context.declaredEntries)) {
            moduleIndex(fileSystem, FsPath.of("/repo"), moduleResolver)
        }
    }

/** What `katachiTemplate` writes for [values], against [fileSystem]'s modules. */
private fun Architecture.writtenPath(fileSystem: KatachiFileSystem, specifier: String, values: Map<String, String>): String =
    process(fileSystem) { context ->
        templateFilesFor(context, listOf(specifier), values) { moduleIndex(fileSystem, FsPath.of("/repo"), moduleResolver) }
    }.keys.single()

private fun Architecture.templateDescriptionJson(fileSystem: KatachiFileSystem): String = withTempProject { root ->
    val output = File(root, "templateDescription.json")
    val context = FakeArchitectureProcessContext(
        architecture = this,
        args = DescribeTemplates.Args(format = DescribeTemplatesFormat.Json, output = output.path),
        fileSystem = fileSystem,
    )
    DescribeTemplates.process(context).getOrThrow()
    output.readText()
}

/**
 * `modulePlacements[]` of the JSON: where a template below a module capture writes, for each
 * module that exists -- the part of the file's path `details[].files[].path` leaves `null`.
 */
class DescribeTemplatesModulePlacementSpec : FreeSpec({
    "モジュールの capture の下のファイルは、今あるモジュールごとに値・ディレクトリ・生成先のパスが出る" {
        val placement = featureComponent().placements(projectWith("home", "settings")).single()

        placement.template shouldBe "feature.Component"
        placement.modulePattern shouldBe ":feature:*"
        placement.captureNames shouldContainExactly listOf("feature")
        placement.modules.map { it.values } shouldContainExactly listOf(listOf("home"), listOf("settings"))
        placement.modules.map { it.modulePath } shouldContainExactly listOf(":feature:home", ":feature:settings")
        placement.modules.map { it.directory } shouldContainExactly listOf("feature/home", "feature/settings")
        placement.modules.map { it.path } shouldContainExactly listOf(
            "feature/home/src/main/kotlin/feature/home/Home\${name}.kt",
            "feature/settings/src/main/kotlin/feature/settings/Settings\${name}.kt",
        )
    }

    "ModuleResolver でディレクトリを変えたモジュールは、そのディレクトリの下のパスで出る" {
        val resolver = ModuleResolver { module ->
            if (module.value == ":feature:settings") "apps/settings-screen" else module.segments.joinToString("/")
        }
        val placement = featureComponent(resolver).placements(projectWith("home", "settings")).single()

        placement.modules.associate { it.modulePath to it.directory } shouldContainExactly mapOf(
            ":feature:home" to "feature/home",
            ":feature:settings" to "apps/settings-screen",
        )
        placement.modules.single { it.modulePath == ":feature:settings" }.path shouldBe
            "apps/settings-screen/src/main/kotlin/feature/settings/Settings\${name}.kt"
    }

    "どのモジュールでも、JSON のパスに値を入れたものが katachiTemplate の書くパスと同じ（ディレクトリを変えたモジュールも）" {
        val resolver = ModuleResolver { module ->
            if (module.value == ":feature:settings") "apps/settings-screen" else module.segments.joinToString("/")
        }
        val fileSystem = projectWith("home", "settings", "user-profile")
        val arch = featureComponent(resolver)

        val placement = arch.placements(fileSystem).single()

        placement.modules.size shouldBe 3
        for (choice in placement.modules) {
            val values = placement.captureNames.zip(choice.values).toMap() + ("name" to "Card")
            arch.writtenPath(fileSystem, "feature.Component", values) shouldBe choice.path.replace("\${name}", "Card")
        }
    }

    "当てはまるモジュールが1つも無ければ modules は空で、テンプレート自体は出る" {
        val placement = featureComponent().placements(projectWith()).single()

        placement.modules.shouldBeEmpty()
    }

    "JSON の modulePlacements に出て、details の files[].path は null のまま（katachiTemplates の表示と同じ）" {
        val json = featureComponent().templateDescriptionJson(projectWith("home"))

        json shouldContain "\"path\": null"
        json shouldContain "\"modulePlacements\": [\n"
        json shouldContain "\"captureNames\": [\"feature\"]"
        json shouldContain "\"values\": [\"home\"]"
        json shouldContain "\"directory\": \"feature/home\""
        json shouldContain "\"path\": \"feature/home/src/main/kotlin/feature/home/Home\${name}.kt\""
    }

    "モジュールの capture の無い定義はモジュールを探さない（modulePlacements は空）" {
        val arch = architectureOf {
            "domain".group {
                "UseCase" {
                    layout {
                        "useCase" / "${capture("name")}UseCase.kt".file().template { "class ${captureValue("name")}UseCase" }
                    }
                }
            }
        }

        val placements = arch.process(ForbiddenFileSystem) { context ->
            // An AssertionError is not caught along the way: listing the modules fails the spec.
            modulePlacementsOf(declaredTemplatesOf(context.declaredEntries)) { throw AssertionError("the modules were listed") }
        }

        placements.shouldBeEmpty()
        arch.templateDescriptionJson(ForbiddenFileSystem) shouldContain "\"modulePlacements\": []"
    }
})
