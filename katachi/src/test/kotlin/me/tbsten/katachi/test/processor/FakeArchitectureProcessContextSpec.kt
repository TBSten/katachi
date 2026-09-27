package me.tbsten.katachi.test.processor

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.processor.ArchitectureProcessNoArgContext
import me.tbsten.katachi.processor.ArchitectureProcessorNoArg
import me.tbsten.katachi.processor.FakeArchitectureProcessContext
import me.tbsten.katachi.processor.internal.process
import me.tbsten.katachi.processor.internal.withArgs
import me.tbsten.katachi.processor.process
import me.tbsten.katachi.test.check.architectureOf
import me.tbsten.katachi.test.check.repositoryOf
import me.tbsten.katachi.test.dsl.files.ForbiddenFileSystem

class FakeArchitectureProcessContextSpec : FreeSpec({
    val definition = architectureOf {
        "domain".group {
            "UseCase" { layout { "useCase" / "*UseCase".ktFile() } }
        }
    }

    "processor が log に出したメッセージが順に記録される" {
        val context = FakeArchitectureProcessContext(
            architecture = definition,
            args = Unit,
            fileSystem = ForbiddenFileSystem,
        )

        LoggingProcessor.process(context).getOrThrow()

        context.logs shouldBe listOf("start", "found 1 roles", "done")
    }

    "log を呼ばない processor では logs が空のまま" {
        val context = FakeArchitectureProcessContext(
            architecture = definition,
            args = Unit,
            fileSystem = ForbiddenFileSystem,
        )

        SilentProcessor.process(context).getOrThrow()

        context.logs.shouldBeEmpty()
    }

    "Fake の Context からも declaredEntries と filesOf が本物と同じ答えを返す" {
        val tree = repositoryOf { "useCase" { "GetUserUseCase.kt"() } }

        val throughProcess = definition.process(tree) { context ->
            context.declaredEntries.map { it.path } to context.filesOf(context.roles.single())
        }

        val fakeContext = FakeArchitectureProcessContext(
            architecture = definition,
            args = Unit,
            fileSystem = tree,
        )
        val throughFake = fakeContext.declaredEntries.map { it.path } to
            fakeContext.filesOf(fakeContext.roles.single())

        throughFake shouldBe throughProcess
    }

    "withArgs で派生させても log は Fake に記録され続ける" {
        val context = FakeArchitectureProcessContext(
            architecture = definition,
            args = Unit,
            fileSystem = ForbiddenFileSystem,
        )

        context.withArgs(Unit).log("derived")

        context.logs shouldBe listOf("derived")
    }
})

private object LoggingProcessor : ArchitectureProcessorNoArg<Unit> {
    override fun process(context: ArchitectureProcessNoArgContext): Result<Unit> = runCatching {
        context.log("start")
        context.log("found ${context.roles.size} roles")
        context.log("done")
    }
}

private object SilentProcessor : ArchitectureProcessorNoArg<Unit> {
    override fun process(context: ArchitectureProcessNoArgContext): Result<Unit> = runCatching { }
}
