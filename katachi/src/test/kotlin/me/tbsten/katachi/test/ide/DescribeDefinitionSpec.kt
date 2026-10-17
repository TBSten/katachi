package me.tbsten.katachi.test.ide

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import java.io.File
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.gradle.gradle
import me.tbsten.katachi.dsl.wholeTree
import me.tbsten.katachi.ide.DescribeDefinition
import me.tbsten.katachi.ide.KatachiDefinitionJsonIoException
import me.tbsten.katachi.ide.internal.DefinitionProblemKind
import me.tbsten.katachi.ide.internal.backendOf
import me.tbsten.katachi.processor.internal.FakeArchitectureProcessContext
import me.tbsten.katachi.test.dsl.files.fakeFileSystem
import me.tbsten.katachi.test.dsl.silent
import me.tbsten.katachi.test.template.templateSetArchitecture
import me.tbsten.katachi.test.template.withTempProject

/**
 * `DescribeDefinition`: the definition JSON the IDE plugin reads (V1 of the IDE definition model's
 * plan). The whole file is compared with a golden for three synthetic definitions; the rest pins
 * one part at a time.
 */
class DescribeDefinitionSpec : FreeSpec({
    "golden" - {
        "basic: group・テンプレート・anyFile・ignore・DirectOnly・ルート直下の役割" {
            checkDefinitionGolden("basic.json", basicDefinition().definitionJsonText(basicProject()))
        }
        "modules: wildcard の module key が実在する 2 モジュールに展開される" {
            checkDefinitionGolden("modules.json", modulesDefinition().definitionJsonText(modulesProject()))
        }
        "explained: 宣言の説明を持つ制約・持たない制約・投げる制約" {
            checkDefinitionGolden("explained.json", explainedDefinition().definitionJsonText(explainedProject()))
        }
    }

    "書き出し" - {
        "[OK] の行は件数を言い、書いた先をログに残す" {
            withTempProject { root ->
                val output = File(root, "out/definition.json")
                val context = FakeArchitectureProcessContext(basicDefinition(), DescribeDefinition.Args(output = output.path), basicProject())
                val summary = DescribeDefinition.process(context).getOrThrow()
                summary.toString() shouldBe "3 roles, 18 entries, 3 constraints, 1 template"
                context.logs.single() shouldStartWith "Wrote file://"
                output.readText() shouldStartWith "{\n  \"schemaVersion\": 1,\n"
            }
        }

        "書けない先は KatachiDefinitionJsonIoException の failure で、例外は外に出ない" {
            withTempProject { root ->
                val directory = File(root, "taken").apply { mkdirs() }
                val context = FakeArchitectureProcessContext(basicDefinition(), DescribeDefinition.Args(output = directory.path), basicProject())
                val failure = DescribeDefinition.process(context).exceptionOrNull()
                (failure is KatachiDefinitionJsonIoException) shouldBe true
                failure?.message.orEmpty() shouldContain "Cannot write the definition JSON"
            }
        }
    }

    "gradle()" - {
        "gradle() の BuildScript の build.gradle.kts は synthetic ではなく、hook の制約が constraints に出る" {
            val project = fakeFileSystem(workingDirectory = "/repo") {
                "/repo" {
                    "gradlew"()
                    "settings.gradle.kts"()
                    "build.gradle.kts"()
                    "app" { "build.gradle.kts"() }
                }
            }
            val definition = architecture {
                files = wholeTree()
                gradle(requireWrapper = false) {
                    buildScript { fileConstraint("build scripts", check = silent()) }
                }
            }.definitionOf(project)

            val buildScript = definition.roles.single { it.qualifiedName == "Gradle.BuildScript" }
            val scripts = buildScript.layout.filter { it.path.endsWith("build.gradle.kts") || it.path.endsWith("build.gradle") }
            scripts.map { it.path }.sorted() shouldBe listOf("app/build.gradle", "app/build.gradle.kts", "build.gradle", "build.gradle.kts")
            scripts.map { it.synthetic }.distinct() shouldBe listOf(false)
            buildScript.layout.filter { it.path == "build" || it.path.endsWith("/build") }.map { it.synthetic }.distinct() shouldBe listOf(true)
            buildScript.constraints.map { it.name } shouldBe listOf("build scripts")
        }
    }

    "walk" - {
        "ルートが無いと WalkFailed で、entries は宣言のまま matchedCount は null" {
            val definition = basicDefinition().definitionOf(rootlessProject())
            definition.problems.map { it.kind } shouldContainExactly listOf(DefinitionProblemKind.WalkFailed)
            definition.problems.single().message shouldContain "KatachiProjectRootNotFoundException"
            definition.projectRoot.shouldBeNull()
            val entries = definition.roles.flatMap { it.layout }
            entries.map { it.matchedCount }.distinct() shouldBe listOf(null)
            entries.flatMap { it.matchedFiles }.shouldBeEmpty()
            definition.roles.flatMap { it.constraints }.flatMap { it.entries }.size shouldBe 4
        }

        "wildcard の module key は walk が失敗すると展開されず ModulesNotListed も出る" {
            val definition = modulesDefinition().definitionOf(rootlessProject())
            definition.problems.map { it.kind } shouldContainExactly
                listOf(DefinitionProblemKind.WalkFailed, DefinitionProblemKind.ModulesNotListed)
            val layout = definition.roles.single().layout
            layout.filter { it.path.startsWith("feature/home") || it.path.startsWith("feature/settings") }.shouldBeEmpty()
            layout.mapNotNull { it.matchedCount }.shouldBeEmpty()
        }

        "maxMatchedFiles で打ち切っても matchedCount は正確" {
            val useCase = basicDefinition().definitionOf(basicProject(), maxMatchedFiles = 1)
                .roles.first { it.qualifiedName == "domain.UseCase" }.layout.first { it.kind == "File" }
            useCase.matchedFiles shouldContainExactly listOf("core/domain/src/main/kotlin/useCase/GetUserUseCase.kt")
            useCase.matchedCount shouldBe 2
            useCase.matchedFilesTruncated shouldBe true
        }

        "maxMatchedFiles = 0 は一覧を書かない" {
            val entries = basicDefinition().definitionOf(basicProject(), maxMatchedFiles = 0).roles.flatMap { it.layout }
            entries.flatMap { it.matchedFiles }.shouldBeEmpty()
            entries.mapNotNull { it.matchedCount }.sum() shouldBe 6
        }
    }

    "テンプレートの組" - {
        "プレビューできない組は TemplateSetPreviewFailed になり、できる組と他のテンプレートは問題にならない" {
            val definition = templateSetArchitecture {
                template("feature.FeatureScreen") { arg("typo", "x") }
            }.definitionOf(basicProject())
            val problems = definition.problems.filter { it.kind == DefinitionProblemKind.TemplateSetPreviewFailed }
            problems.map { it.detail } shouldContainExactly listOf("feature.NewFeature")
            definition.problems.none { it.kind == DefinitionProblemKind.TemplatePreviewFailed } shouldBe true
            definition.templates.templateSets.single().conflict shouldBe true
            definition.templates.templateSetDetails.shouldBeEmpty()
        }

        "プレビューできる組は templates に詳細ごと載り、問題は出ない" {
            val definition = templateSetArchitecture {
                val name by stringParameter()
                template("feature.FeatureScreen") { arg("name", name) }
            }.definitionOf(basicProject())
            definition.problems.none { it.kind == DefinitionProblemKind.TemplateSetPreviewFailed } shouldBe true
            definition.templates.templateSetDetails.map { it.templateSet } shouldContainExactly listOf("feature.NewFeature")
        }
    }

    "テンプレートの置き場所" - {
        "placements が読めなかった理由は、walk が成功していれば ModulesNotListed に 1 件書く" {
            val definition = modulesDefinition().definitionOf(modulesProject(), placementsFailure = IllegalStateException("boom\nsecond line"))
            val problem = definition.problems.single()
            problem.kind shouldBe DefinitionProblemKind.ModulesNotListed
            problem.message shouldContain "IllegalStateException: boom"
            (problem.message.contains("second line")) shouldBe false
        }

        "walk も失敗していれば WalkFailed が理由を言うので足さない" {
            val definition = modulesDefinition().definitionOf(rootlessProject(), placementsFailure = IllegalStateException("boom"))
            definition.problems.count { it.message.contains("boom") } shouldBe 0
        }
    }

    "module { } が足す entry" - {
        "build.gradle.kts の File と build の Ignore も layout に出て、synthetic と place の旗が付く" {
            val layout = modulesDefinition().definitionOf(modulesProject()).roles.single().layout
            for (module in listOf("feature/home", "feature/settings")) {
                val script = layout.single { it.path == "$module/build.gradle.kts" }
                script.kind shouldBe "File"
                script.matchedFiles shouldContainExactly listOf("$module/build.gradle.kts")
                layout.single { it.path == "$module/build" }.kind shouldBe "Ignore"
            }
            for (module in listOf("feature/home", "feature/settings")) {
                layout.single { it.path == "$module/build.gradle.kts" }.synthetic shouldBe true
                layout.single { it.path == "$module/build" }.synthetic shouldBe true
                layout.single { it.path == module }.place shouldBe true
            }
            // What the user wrote carries neither.
            layout.filter { it.path.endsWith("Screen.kt") || it.path.endsWith("/kotlin") }.forEach { (it.synthetic || it.place) shouldBe false }
        }

        "constraints[].entries は出した layout の index を指す" {
            val role = basicDefinition().definitionOf(basicProject()).roles.first { it.qualifiedName == "domain.UseCase" }
            val constraint = role.constraints.single()
            constraint.entries.map { role.layout[it].index } shouldBe constraint.entries
        }
    }

    "constraints" - {
        "DirectOnly はブロック直下の entry だけ、Subtree は配下すべて" {
            val model = basicDefinition().definitionOf(basicProject()).roles.first { it.qualifiedName == "domain.Model" }
            val byName = model.constraints.associateBy { it.name }
            val direct = byName.getValue("only the directory itself")
            direct.range shouldBe "DirectOnly"
            direct.entries.map { model.layout[it].path } shouldContainExactly listOf("core/domain/src/main/kotlin/model")
            byName.getValue("the whole role").entries.map { model.layout[it].path } shouldContainExactly listOf(
                "core/domain/src/main/kotlin/model",
                "core/domain/src/main/kotlin/model/mapper/*Mapper.kt",
            )
        }

        "backend はクラス名の接頭辞で 4 通り" {
            backendOf("me.tbsten.katachi.konsist.declarations.internal.KtFileContentConstraint") shouldBe "ktFileContent"
            backendOf("me.tbsten.katachi.konsist.internal.KonsistFileConstraint") shouldBe "konsist"
            backendOf("me.tbsten.katachi.konture.internal.KontureFileConstraint") shouldBe "konture"
            backendOf("com.example.OurOwnConstraint") shouldBe "custom"
            backendOf("me.tbsten.katachi.konsistent.Other") shouldBe "custom"
        }
    }

    "fileDeclarations" - {
        "explainDeclarations = false なら説明は null で、投げる制約も呼ばれない" {
            val definition = explainedDefinition().definitionOf(explainedProject(), explainDeclarations = false)
            definition.roles.flatMap { it.constraints }.map { it.fileDeclarations } shouldBe listOf(null, null, null)
            definition.problems.shouldBeEmpty()
        }

        "DeclarationExplaining を実装しない制約は null" {
            val service = explainedDefinition().definitionOf(explainedProject()).roles.first { it.name == "Service" }
            service.constraints.first { it.name == "does not" }.fileDeclarations.shouldBeNull()
            service.constraints.first { it.name == "explains itself" }.fileDeclarations.shouldNotBeNull().map { it.file } shouldBe listOf(
                "src/service/Broken.kt",
                "src/service/HealthService.kt",
                "src/service/UserServiceBad.kt",
            )
        }

        "投げた制約は null と ExplanationFailed（detail に役割）で、ほかは答える" {
            val definition = explainedDefinition().definitionOf(explainedProject())
            definition.roles.first { it.name == "Thrower" }.constraints.single().fileDeclarations.shouldBeNull()
            val problem = definition.problems.single()
            problem.kind shouldBe DefinitionProblemKind.ExplanationFailed
            problem.detail shouldBe "Thrower"
            problem.message shouldContain "IllegalStateException: cannot explain Thrower"
            (problem.message.contains("second line")) shouldBe false
        }

        "maxExplainedFiles で打ち切ると先頭だけを説明し、ExplanationTruncated を 1 つ足す" {
            val definition = explainedDefinition().definitionOf(explainedProject(), maxExplainedFiles = 2)
            val explained = definition.roles.first { it.name == "Service" }.constraints.first { it.name == "explains itself" }
            explained.fileDeclarations.shouldNotBeNull().map { it.file } shouldBe listOf("src/service/Broken.kt", "src/service/HealthService.kt")
            definition.roles.first { it.name == "Thrower" }.constraints.single().fileDeclarations.shouldBeNull()
            definition.problems.map { it.kind } shouldContainExactly listOf(DefinitionProblemKind.ExplanationTruncated)
        }

        "line を持たない宣言は書かず、line は常に int" {
            val definition = unplacedDefinition().definitionOf(unplacedProject())
            val declarations = definition.roles.single().constraints.single().fileDeclarations.shouldNotBeNull().single().declarations
            declarations.map { it.name } shouldContainExactly listOf("Placed")
            declarations.single().line shouldBe 3
        }

        "落ちたのが .kt 以外だけなら、予算が .kt を全部入れていれば ExplanationTruncated も ExplanationFailed も出ない" {
            val definition = budgetDefinition().definitionOf(budgetProject(), maxExplainedFiles = 2)
            definition.roles.single().constraints.single().fileDeclarations.shouldNotBeNull().map { it.file } shouldContainExactly
                listOf("docs/c.kt", "docs/d.kt")
            definition.problems.shouldBeEmpty()
        }

        "予算が .kt の途中で尽きても .md は渡し続け、.kt だけを落として ExplanationTruncated を 1 つ" {
            val definition = budgetDefinition().definitionOf(budgetProject(), maxExplainedFiles = 1)
            definition.roles.single().constraints.single().fileDeclarations.shouldNotBeNull().map { it.file } shouldContainExactly
                listOf("docs/c.kt")
            definition.problems.map { it.kind } shouldContainExactly listOf(DefinitionProblemKind.ExplanationTruncated)
        }

        "予算が 0 なら .kt を 1 つも説明せず null で、ExplanationTruncated だけ" {
            val definition = budgetDefinition().definitionOf(budgetProject(), maxExplainedFiles = 0)
            definition.roles.single().constraints.single().fileDeclarations.shouldBeNull()
            definition.problems.map { it.kind } shouldContainExactly listOf(DefinitionProblemKind.ExplanationTruncated)
        }

        "walk が失敗したら説明しない" {
            val definition = explainedDefinition().definitionOf(rootlessProject())
            definition.roles.flatMap { it.constraints }.map { it.fileDeclarations }.distinct() shouldBe listOf(null)
            definition.problems.map { it.kind } shouldContainExactly listOf(DefinitionProblemKind.WalkFailed)
        }
    }
})
