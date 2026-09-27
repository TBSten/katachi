package me.tbsten.katachi.test.processor

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.AbstractDecoder
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import me.tbsten.katachi.dsl.files.FsPath
import me.tbsten.katachi.dsl.files.KatachiFileSystem
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.processor.ArchitectureProcessContext
import me.tbsten.katachi.processor.ArchitectureProcessNoArgContext
import me.tbsten.katachi.processor.ArchitectureProcessor
import me.tbsten.katachi.processor.ArchitectureProcessorNoArg
import me.tbsten.katachi.processor.KatachiProcessorArgsDecoderException
import me.tbsten.katachi.processor.decodeFromStringMap
import me.tbsten.katachi.processor.internal.declaredArgNames
import me.tbsten.katachi.processor.internal.process
import me.tbsten.katachi.processor.plus
import me.tbsten.katachi.processor.process
import me.tbsten.katachi.test.check.architectureOf
import me.tbsten.katachi.test.check.layoutArchitecture
import me.tbsten.katachi.test.check.repositoryOf

class ProcessorCompositionSpec : FreeSpec({
    val definition = architectureOf {
        "domain".group {
            "UseCase" { layout { "useCase" / "*UseCase".ktFile() } }
            "Repository" { layout { "repository" / "*Repository".ktFile() } }
        }
    }

    "+ で合成した processor は両方の結果を順につなげる" {
        val combined = FirstNames + SecondNames

        definition.process(combined, Unit to Unit).getOrThrow() shouldBe
            definition.process(FirstNames).getOrThrow() + definition.process(SecondNames).getOrThrow()
    }

    "片方が failure を返すと、合成した processor もその failure を返す" {
        val failure = IllegalStateException("second said no")

        val result = definition.process(FirstNames + FailingNames(failure), Unit to Unit)

        result.exceptionOrNull() shouldBe failure
    }

    "両方が failure を返すと、先の failure を返し、後の failure は suppressed に残る" {
        val first = IllegalStateException("first said no")
        val second = IllegalStateException("second said no")

        val result = definition.process(FailingNames(first) + FailingNames(second), Unit to Unit)

        result.exceptionOrNull() shouldBe first
        first.suppressed.toList() shouldBe listOf(second)
    }

    "先が failure を返しても、後の processor は走る" {
        val ran = mutableListOf<String>()
        val second = RecordingNames(ran)

        definition.process(FailingNames(IllegalStateException("no")) + second, Unit to Unit)

        ran shouldBe listOf("ran")
    }

    "+ で合成しても走査は1回しか起きない" {
        fun listCallsWhenCombined(): Int {
            val tree = CompositionCountingFileSystem(repositoryOf { "useCase" { "GetUserUseCase.kt"() } })
            val target = layoutArchitecture { "useCase" / "*UseCase".ktFile() }
            val combined = FilesCount + FilesCount
            target.process(combined, Unit to Unit, tree)
            return tree.listCalls
        }

        fun listCallsWhenSingle(): Int {
            val tree = CompositionCountingFileSystem(repositoryOf { "useCase" { "GetUserUseCase.kt"() } })
            val target = layoutArchitecture { "useCase" / "*UseCase".ktFile() }
            target.process(FilesCount, tree)
            return tree.listCalls
        }

        listCallsWhenCombined() shouldBe listCallsWhenSingle()
    }

    "合成した argsSerializer の既知キーは両方の和集合になる" {
        val combined = ComposedRoleNameProcessor + CountProcessor

        declaredArgNames(combined) shouldContain "roleName"
        declaredArgNames(combined) shouldContain "count"
    }

    "両方が同じ名前のフィールドを持つとき、同じ値が両方に配られる" {
        val combined = ComposedRoleNameProcessor + ComposedRoleNameProcessor2

        decodeFromStringMap(combined.argsSerializer, mapOf("roleName" to "X")) shouldBe
            (ComposedRoleNameArgs("X") to ComposedRoleNameArgs2("X"))
    }

    "合成した argsSerializer を別の Decoder に渡すと KatachiProcessorArgsDecoderException になる" {
        val combined = ComposedRoleNameProcessor + CountProcessor

        shouldThrow<KatachiProcessorArgsDecoderException> {
            combined.argsSerializer.deserialize(NotAStringMapDecoder)
        }
    }
})

/** Any [kotlinx.serialization.encoding.Decoder] other than `StringMapDecoder`. */
private object NotAStringMapDecoder : AbstractDecoder() {
    override val serializersModule: SerializersModule = EmptySerializersModule()

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int = CompositeDecoder.DECODE_DONE
}

/** Answers "the run does not pass" with [failure], having done its job. */
private class FailingNames(private val failure: Throwable) : ArchitectureProcessorNoArg<List<String>> {
    override fun process(context: ArchitectureProcessNoArgContext): Result<List<String>> =
        runCatching { throw failure }
}

/** Passes with nothing, and leaves a mark in [ran] that it was run at all. */
private class RecordingNames(private val ran: MutableList<String>) : ArchitectureProcessorNoArg<List<String>> {
    override fun process(context: ArchitectureProcessNoArgContext): Result<List<String>> = runCatching {
        ran += "ran"
        emptyList()
    }
}

private object FirstNames : ArchitectureProcessorNoArg<List<String>> {
    override fun process(context: ArchitectureProcessNoArgContext): Result<List<String>> =
        runCatching { context.roles.map { "first:${it.qualifiedName}" } }
}

private object SecondNames : ArchitectureProcessorNoArg<List<String>> {
    override fun process(context: ArchitectureProcessNoArgContext): Result<List<String>> =
        runCatching { context.roles.map { "second:${it.qualifiedName}" } }
}

/** A processor that returns the files of every role it can see, to prove walk sharing. */
private object FilesCount : ArchitectureProcessorNoArg<List<String>> {
    override fun process(context: ArchitectureProcessNoArgContext): Result<List<String>> =
        runCatching { context.roles.flatMap { context.filesOf(it) } }
}

@Serializable
private data class ComposedRoleNameArgs(val roleName: String)

@Serializable
private data class ComposedRoleNameArgs2(val roleName: String)

@Serializable
private data class CountArgs(val count: Int = 1)

private object ComposedRoleNameProcessor : ArchitectureProcessor<ComposedRoleNameArgs, List<String>> {
    override val argsSerializer: KSerializer<ComposedRoleNameArgs> = ComposedRoleNameArgs.serializer()

    override fun process(context: ArchitectureProcessContext<ComposedRoleNameArgs>): Result<List<String>> =
        runCatching { listOf(context.args.roleName) }
}

private object ComposedRoleNameProcessor2 : ArchitectureProcessor<ComposedRoleNameArgs2, List<String>> {
    override val argsSerializer: KSerializer<ComposedRoleNameArgs2> = ComposedRoleNameArgs2.serializer()

    override fun process(context: ArchitectureProcessContext<ComposedRoleNameArgs2>): Result<List<String>> =
        runCatching { listOf(context.args.roleName) }
}

private object CountProcessor : ArchitectureProcessor<CountArgs, List<String>> {
    override val argsSerializer: KSerializer<CountArgs> = CountArgs.serializer()

    override fun process(context: ArchitectureProcessContext<CountArgs>): Result<List<String>> =
        runCatching { List(context.args.count) { "x" } }
}

/** A tree that answers normally and remembers how often it was listed. */
private class CompositionCountingFileSystem(private val delegate: KatachiFileSystem) : KatachiFileSystem {
    var listCalls: Int = 0
        private set

    override val workingDirectory: FsPath get() = delegate.workingDirectory

    override fun exists(path: FsPath): Boolean = delegate.exists(path)

    override fun isDirectory(path: FsPath): Boolean = delegate.isDirectory(path)

    override fun list(directory: FsPath): List<FsPath> {
        listCalls++
        return delegate.list(directory)
    }

    override fun toString(): String = "CountingFileSystem($delegate)"
}
