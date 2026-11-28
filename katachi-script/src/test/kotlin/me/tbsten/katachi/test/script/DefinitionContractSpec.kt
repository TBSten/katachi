package me.tbsten.katachi.test.script

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readText
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.gradle.gradle
import me.tbsten.katachi.ide.internal.DescribeDefinition
import me.tbsten.katachi.processor.process
import me.tbsten.katachi.script.internal.readDefinitionModel

/** What `lint` reads is what katachi's own processor writes: a key renamed in :katachi fails here, where the hand-made JSON of LintCommandSpec would not. */
class DefinitionContractSpec : FreeSpec({
    "katachi が katachiInternalDefinitionJson で書く JSON を、lint の読み取りがそのまま読める" {
        val output = createTempDirectory("katachi-definition-").resolve("definition.json")
        architecture {
            gradle { }
            "Domain".group {
                documented = false
                "UseCase".role {
                    layout {
                        "src" {
                            "main" { "UseCase".file() }
                        }
                        ".github" { anyFile() }
                        "build".ignore()
                    }
                    fileConstraint("never rejects") { emptyList() }
                }
            }
        }.process(DescribeDefinition, DescribeDefinition.Args(output = output.toString())).getOrThrow()

        val model = readDefinitionModel(output.readText())

        val useCase = model.roles.single { it.name == "UseCase" }
        useCase.groupPath shouldBe listOf("Domain")
        useCase.declaredAt!!.fileName shouldBe "DefinitionContractSpec.kt"
        useCase.documented shouldBe null
        useCase.constraints shouldBe 1
        useCase.declaredByGradle shouldBe false
        useCase.layout.filter { !it.synthetic }.map { it.kind to it.path } shouldContainExactly
            listOf("Directory" to "src", "Directory" to "src/main", "File" to "src/main/UseCase", "AnyFile" to ".github", "Ignore" to "build")

        model.groups.single { it.name == "Domain" }.documented shouldBe false
    }

    "gradle() が宣言した group と Role には declaredByGradle が付き、利用者が gradle { } に書いた Role には付かない" {
        val output = createTempDirectory("katachi-definition-").resolve("definition.json")
        architecture {
            gradle {
                "DependencyVerification".role { layout { "gradle/verification-metadata.xml".file() } }
            }
        }.process(DescribeDefinition, DescribeDefinition.Args(output = output.toString())).getOrThrow()

        val model = readDefinitionModel(output.readText())

        model.groups.filter { it.declaredByGradle }.map { it.path } shouldContainExactly listOf(listOf("Gradle"), listOf("Gradle", "GradleWrapper"))
        model.roles.filter { !it.declaredByGradle }.map { it.name } shouldContainExactly listOf("DependencyVerification")
        model.roles.count { it.declaredByGradle } shouldBe 8
    }
})
