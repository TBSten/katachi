package me.tbsten.katachi.test.processor

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.processor.ArchitectureProcessor
import me.tbsten.katachi.processor.ProjectModel
import me.tbsten.katachi.processor.process
import me.tbsten.katachi.test.check.architectureOf
import me.tbsten.katachi.test.check.layoutArchitecture
import me.tbsten.katachi.test.check.repositoryOf
import me.tbsten.katachi.test.fs.ForbiddenFileSystem

class ArchitectureProcessorSpec : FreeSpec({
    val definition = architectureOf {
        "domain".group {
            "UseCase" { layout { "useCase" / "*UseCase".ktFile() } }
            "Repository" { layout { "repository" / "*Repository".ktFile() } }
        }
    }

    "書き方" - {
        "クラスとして書いた processor を渡せる" {
            definition.process(RoleNames(), ForbiddenFileSystem) shouldBe
                listOf("domain/UseCase", "domain/Repository")
        }

        "ラムダとして書いた processor でも同じ結果になる" {
            definition.process(ForbiddenFileSystem) { model ->
                model.roles.map { it.qualifiedName }
            } shouldBe definition.process(RoleNames(), ForbiddenFileSystem)
        }

        "戻り値が Unit の processor は副作用だけを起こせる" {
            val written = mutableListOf<String>()

            definition.process(CollectRoleNames(write = { written += it }), ForbiddenFileSystem)

            written shouldBe listOf("domain/UseCase", "domain/Repository")
        }
    }

    "モデルの受け渡し" - {
        "processor は自分が要求したファイルだけを受け取る" {
            val tree = repositoryOf { "useCase" { "GetUserUseCase.kt"() } }

            layoutArchitecture { "useCase" / "*UseCase".ktFile() }
                .process(FilesOfEveryRole(), tree) shouldBe
                mapOf("app/Role" to listOf("useCase/GetUserUseCase.kt"))
        }
    }
})

/** A processor with no settings: everything it needs is in the model. */
private class RoleNames : ArchitectureProcessor<List<String>> {
    override fun process(model: ProjectModel): List<String> = model.roles.map { it.qualifiedName }
}

/** A processor whose result is its effect, with the effect injected rather than hard-wired. */
private class CollectRoleNames(private val write: (String) -> Unit) : ArchitectureProcessor<Unit> {
    override fun process(model: ProjectModel) {
        for (role in model.roles) write(role.qualifiedName)
    }
}

private class FilesOfEveryRole : ArchitectureProcessor<Map<String, List<String>>> {
    override fun process(model: ProjectModel): Map<String, List<String>> =
        model.roles.associate { it.qualifiedName to model.filesOf(it) }
}
