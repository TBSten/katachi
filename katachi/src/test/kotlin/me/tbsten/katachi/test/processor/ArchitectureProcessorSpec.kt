package me.tbsten.katachi.test.processor

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.processor.ArchitectureProcessContext
import me.tbsten.katachi.processor.ArchitectureProcessorNoArg
import me.tbsten.katachi.processor.internal.process
import me.tbsten.katachi.processor.process
import me.tbsten.katachi.test.check.architectureOf
import me.tbsten.katachi.test.check.layoutArchitecture
import me.tbsten.katachi.test.check.repositoryOf
import me.tbsten.katachi.test.dsl.files.ForbiddenFileSystem

class ArchitectureProcessorSpec : FreeSpec({
    val definition = architectureOf {
        "domain".group {
            "UseCase" { layout { "useCase" / "*UseCase".ktFile() } }
            "Repository" { layout { "repository" / "*Repository".ktFile() } }
        }
    }

    "書き方" - {
        "クラスとして書いた processor を渡せる" {
            definition.process(RoleNames, ForbiddenFileSystem).getOrThrow() shouldBe
                listOf("domain/UseCase", "domain/Repository")
        }

        "ラムダとして書いた processor でも同じ結果になる" {
            definition.process(ForbiddenFileSystem) { context ->
                context.roles.map { it.qualifiedName }
            } shouldBe definition.process(RoleNames, ForbiddenFileSystem).getOrThrow()
        }

        "戻り値が Unit の processor は副作用だけを起こせる" {
            val written = mutableListOf<String>()

            definition.process(CollectRoleNames(write = { written += it }), ForbiddenFileSystem).getOrThrow()

            written shouldBe listOf("domain/UseCase", "domain/Repository")
        }

        "object として書いた processor と引数なし class の processor は同じ結果になる" {
            definition.process(RoleNames, ForbiddenFileSystem) shouldBe
                definition.process(RoleNamesAsClass(), ForbiddenFileSystem)
        }
    }

    "モデルの受け渡し" - {
        "processor は自分が要求したファイルだけを受け取る" {
            val tree = repositoryOf { "useCase" { "GetUserUseCase.kt"() } }

            layoutArchitecture { "useCase" / "*UseCase".ktFile() }
                .process(FilesOfEveryRole(), tree).getOrThrow() shouldBe
                mapOf("app/Role" to listOf("useCase/GetUserUseCase.kt"))
        }
    }
})

/** A processor with no settings, written as an object: everything it needs is on the context. */
private object RoleNames : ArchitectureProcessorNoArg<List<String>> {
    override fun process(context: ArchitectureProcessContext<Unit>): Result<List<String>> =
        runCatching { context.roles.map { it.qualifiedName } }
}

/** The same processor as [RoleNames], written as a no-arg class instead of an object. */
private class RoleNamesAsClass : ArchitectureProcessorNoArg<List<String>> {
    override fun process(context: ArchitectureProcessContext<Unit>): Result<List<String>> =
        runCatching { context.roles.map { it.qualifiedName } }
}

/** A processor whose result is its effect, with the effect injected rather than hard-wired. */
private class CollectRoleNames(private val write: (String) -> Unit) : ArchitectureProcessorNoArg<Unit> {
    override fun process(context: ArchitectureProcessContext<Unit>): Result<Unit> = runCatching {
        for (role in context.roles) write(role.qualifiedName)
    }
}

private class FilesOfEveryRole : ArchitectureProcessorNoArg<Map<String, List<String>>> {
    override fun process(context: ArchitectureProcessContext<Unit>): Result<Map<String, List<String>>> =
        runCatching { context.roles.associate { it.qualifiedName to context.filesOf(it) } }
}
