package me.tbsten.katachi.test.processor

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.fs.FsPath
import me.tbsten.katachi.fs.KatachiFileSystem
import me.tbsten.katachi.processor.ArchitectureProcessContext
import me.tbsten.katachi.processor.ArchitectureProcessor
import me.tbsten.katachi.processor.ArchitectureProcessorNoArg
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.processor.KatachiProcessorNotFoundException
import me.tbsten.katachi.processor.KatachiProcessorNotInstantiableException
import me.tbsten.katachi.processor.KatachiProcessorTypeException
import me.tbsten.katachi.processor.internal.instantiateProcessor
import me.tbsten.katachi.processor.internal.runProcessors
import me.tbsten.katachi.test.check.architectureOf

class ProcessorRunSpec : FreeSpec({
    val definition = architectureOf {
        "domain".group { "UseCase" { layout { "useCase" / "*UseCase".ktFile() } } }
    }

    "instantiateProcessor" - {
        "object の processor は INSTANCE 経由で実体化できる" {
            instantiateProcessor(ObjectProcessor::class.java) shouldBe ObjectProcessor
        }

        "引数なし class の processor はコンストラクタ経由で実体化できる" {
            instantiateProcessor(NoArgClassProcessor::class.java).shouldBeInstanceOfNoArgClassProcessor()
        }

        "引数ありコンストラクタしか無い class は INSTANCE と引数なしコンストラクタの両方を探したと言って落ちる" {
            val thrown = shouldThrow<KatachiProcessorNotInstantiableException> {
                instantiateProcessor(RequiresArgumentProcessor::class.java)
            }

            thrown.type shouldBe RequiresArgumentProcessor::class.java
            thrown.message.shouldNotBeNull() shouldContain "INSTANCE"
            thrown.message.shouldNotBeNull() shouldContain "no-argument constructor"
        }

        "ArchitectureProcessor でないクラスは KatachiProcessorTypeException になる" {
            val thrown = shouldThrow<KatachiProcessorTypeException> {
                instantiateProcessor(NotAProcessor::class.java)
            }

            thrown.type shouldBe NotAProcessor::class.java
        }
    }

    "runProcessors" - {
        "未登録キーは登録済みキーの一覧つきで落ちる" {
            val thrown = shouldThrow<KatachiProcessorNotFoundException> {
                runProcessors(
                    architecture = definition,
                    registry = mapOf("known" to ObjectProcessor::class.java),
                    processorKeys = listOf("unknown"),
                    rawArgs = emptyMap(),
                    out = {},
                )
            }

            thrown.key shouldBe "unknown"
            thrown.known shouldContain "known"
        }

        "2つ指定すると1回の実行で両方走り、サマリに両方の成功が出る" {
            val lines = mutableListOf<String>()

            val summary = runProcessors(
                architecture = definition,
                registry = mapOf("a" to ObjectProcessor::class.java, "b" to NoArgClassProcessor::class.java),
                processorKeys = listOf("a", "b"),
                rawArgs = emptyMap(),
                out = lines::add,
            )

            summary.succeeded shouldBe 2
            summary.failed shouldBe 0
            lines.joinToString("\n") shouldContain "2 succeeded, 0 failed"
        }

        "出力フォーマットが設計の見本どおりになる" {
            val lines = mutableListOf<String>()

            runProcessors(
                architecture = definition,
                registry = mapOf("logging" to RunLoggingProcessor::class.java, "boom" to BoomProcessor::class.java),
                processorKeys = listOf("logging", "boom"),
                rawArgs = emptyMap(),
                out = lines::add,
            )

            val text = lines.joinToString("\n")
            text shouldContain "[1/3] Processors: logging, boom"
            text shouldContain "[2/3] Processing..."
            text shouldContain "  [logging] hello"
            text shouldContain "=".repeat(40)
            text shouldContain "[3/3] Katachi processor run: 1 succeeded, 1 failed"
            text shouldContain "[OK] logging"
            text shouldContain "[FAILED] boom"
            text shouldContain "  boom message"
            text.shouldNotContain("|")
        }

        "片方が例外を投げても、もう片方は走り、サマリが 1 succeeded, 1 failed になる" {
            val summary = runProcessors(
                architecture = definition,
                registry = mapOf("ok" to ObjectProcessor::class.java, "boom" to BoomProcessor::class.java),
                processorKeys = listOf("ok", "boom"),
                rawArgs = emptyMap(),
                out = {},
            )

            summary.succeeded shouldBe 1
            summary.failed shouldBe 1
        }

        "failure を返した processor は [FAILED] とメッセージの全行を出し、失敗に数えられる" {
            val lines = mutableListOf<String>()

            val summary = runProcessors(
                architecture = definition,
                registry = mapOf("ok" to ObjectProcessor::class.java, "no" to AnswersNoProcessor::class.java),
                processorKeys = listOf("ok", "no"),
                rawArgs = emptyMap(),
                out = lines::add,
            )

            summary.succeeded shouldBe 1
            summary.failed shouldBe 1
            val failedAt = lines.indexOf("[FAILED] no")
            withClue("複数行のメッセージも、1行目だけでなく全行が processor の下に字下げされて並ぶ") {
                lines.subList(failedAt + 1, failedAt + 3) shouldBe
                    listOf("  2 roles are still named Todo.", "  Rename them before the release.")
            }
        }

        "投げた processor も [FAILED] とメッセージを出し、失敗に数えられる" {
            val lines = mutableListOf<String>()

            val summary = runProcessors(
                architecture = definition,
                registry = mapOf("boom" to BoomProcessor::class.java),
                processorKeys = listOf("boom"),
                rawArgs = emptyMap(),
                out = lines::add,
            )

            summary.failed shouldBe 1
            val failedAt = lines.indexOf("[FAILED] boom")
            lines[failedAt + 1] shouldBe "  boom message"
        }

        "success の Collection は1要素1行で [OK] の下に出る" {
            val lines = mutableListOf<String>()

            runProcessors(
                architecture = definition,
                registry = mapOf("names" to NamesProcessor::class.java),
                processorKeys = listOf("names"),
                rawArgs = emptyMap(),
                out = lines::add,
            )

            val okAt = lines.indexOf("[OK] names")
            lines.subList(okAt + 1, okAt + 3) shouldBe listOf("first", "second")
        }

        "Unit を返す processor は結果を印字せず、非 Unit は印字する" {
            val lines = mutableListOf<String>()

            runProcessors(
                architecture = definition,
                registry = mapOf(
                    "unit" to ObjectProcessor::class.java,
                    "value" to ValueProcessor::class.java,
                ),
                processorKeys = listOf("unit", "value"),
                rawArgs = emptyMap(),
                out = lines::add,
            )

            lines shouldContain "42"
        }

        "未知の --arg は選ばれた processor 全部の和集合に対して1回だけ判定される" {
            // Unit を args に取る processor と roleName を知っている processor を同時に指定して
            // roleName を渡しても通る。
            runProcessors(
                architecture = definition,
                registry = mapOf(
                    "noArg" to ObjectProcessor::class.java,
                    "roleName" to RunRoleNameProcessor::class.java,
                ),
                processorKeys = listOf("noArg", "roleName"),
                rawArgs = mapOf("roleName" to "GetUser"),
                out = {},
            )
        }

        "どの processor も知らない --arg キーは落ちる" {
            shouldThrow<IllegalArgumentException> {
                runProcessors(
                    architecture = definition,
                    registry = mapOf("noArg" to ObjectProcessor::class.java),
                    processorKeys = listOf("noArg"),
                    rawArgs = mapOf("typo" to "x"),
                    out = {},
                )
            }
        }

        "選ばれた processor の undeclaredArgNames は、表明なしに known へ足される" {
            // build.gradle.kts -> 生成コード -> KatachiEntryPoint -> ここ、の最後の繋ぎ目。
            // 表明の仕組みが無くなったので、選ばれてさえいれば常に問い合わせる。
            val summary = runProcessors(
                architecture = definition,
                registry = mapOf("undeclared" to UndeclaredNameRunProcessor::class.java),
                processorKeys = listOf("undeclared"),
                rawArgs = mapOf("greeting" to "hi"),
                out = {},
            )

            summary.failed shouldBe 0
        }

        "実体化や未知キー判定は、どの processor も走る前に終わっている" {
            RanFlag.ran = false

            shouldThrow<KatachiProcessorNotFoundException> {
                runProcessors(
                    architecture = definition,
                    registry = mapOf("first" to RecordsRunProcessor::class.java),
                    processorKeys = listOf("first", "missing"),
                    rawArgs = emptyMap(),
                    out = {},
                )
            }

            RanFlag.ran shouldBe false
        }

        "走査は1回で済む -- 2 processor でも1 processor と同じだけしか list が呼ばれない" {
            fun listCallsFor(keys: List<String>): Int {
                val tree = RunCountingFileSystem(fakeFileSystemFor())
                runProcessors(
                    architecture = definition,
                    registry = mapOf(
                        "a" to FilesReadingProcessorA::class.java,
                        "b" to FilesReadingProcessorB::class.java,
                    ),
                    processorKeys = keys,
                    rawArgs = emptyMap(),
                    fileSystem = tree,
                    out = {},
                )
                return tree.listCalls
            }

            listCallsFor(listOf("a", "b")) shouldBe listCallsFor(listOf("a"))
        }
    }
})

private fun ArchitectureProcessor<*, *>.shouldBeInstanceOfNoArgClassProcessor() {
    this shouldBe NoArgClassProcessor()
}

/** A minimal fake tree that has whatever `findProjectRoot` needs and nothing else. */
private fun fakeFileSystemFor(): KatachiFileSystem =
    object : KatachiFileSystem {
        override val workingDirectory: FsPath = FsPath.of("/repo")
        override fun exists(path: FsPath): Boolean =
            path == workingDirectory || path == workingDirectory / "gradlew"
        override fun isDirectory(path: FsPath): Boolean = path == workingDirectory
        override fun list(directory: FsPath): List<FsPath> = emptyList()
    }

/** Counts calls to [list], the way `ArchitectureProcessContextSpec` proves a walk is shared. */
private class RunCountingFileSystem(private val delegate: KatachiFileSystem) : KatachiFileSystem {
    var listCalls: Int = 0
        private set

    override val workingDirectory: FsPath get() = delegate.workingDirectory

    override fun exists(path: FsPath): Boolean = delegate.exists(path)

    override fun isDirectory(path: FsPath): Boolean = delegate.isDirectory(path)

    override fun list(directory: FsPath): List<FsPath> {
        listCalls++
        return delegate.list(directory)
    }
}

private object ObjectProcessor : ArchitectureProcessorNoArg<Unit> {
    override fun process(context: ArchitectureProcessContext<Unit>): Result<Unit> = runCatching { }
}

private class NoArgClassProcessor : ArchitectureProcessorNoArg<Unit> {
    override fun process(context: ArchitectureProcessContext<Unit>): Result<Unit> = runCatching { }

    override fun equals(other: Any?): Boolean = other is NoArgClassProcessor
    override fun hashCode(): Int = NoArgClassProcessor::class.hashCode()
}

private class RequiresArgumentProcessor(@Suppress("UNUSED_PARAMETER") prefix: String) :
    ArchitectureProcessorNoArg<Unit> {
    override fun process(context: ArchitectureProcessContext<Unit>): Result<Unit> = runCatching { }
}

/** Did its job, and the answer is that the run does not pass. */
private object AnswersNoProcessor : ArchitectureProcessorNoArg<Unit> {
    override fun process(context: ArchitectureProcessContext<Unit>): Result<Unit> =
        runCatching {
            throw IllegalStateException("2 roles are still named Todo.\nRename them before the release.")
        }
}

private object NamesProcessor : ArchitectureProcessorNoArg<List<String>> {
    override fun process(context: ArchitectureProcessContext<Unit>): Result<List<String>> =
        runCatching { listOf("first", "second") }
}

private class NotAProcessor

private object RunLoggingProcessor : ArchitectureProcessorNoArg<Unit> {
    override fun process(context: ArchitectureProcessContext<Unit>): Result<Unit> = runCatching {
        context.log("hello")
    }
}

private object BoomProcessor : ArchitectureProcessorNoArg<Unit> {
    override fun process(context: ArchitectureProcessContext<Unit>): Result<Unit> {
        throw IllegalStateException("boom message")
    }
}

private object ValueProcessor : ArchitectureProcessorNoArg<Int> {
    override fun process(context: ArchitectureProcessContext<Unit>): Result<Int> =
        runCatching { 42 }
}

@Serializable
private data class RunRoleNameArgs(val roleName: String = "")

private object RunRoleNameProcessor : ArchitectureProcessor<RunRoleNameArgs, Unit> {
    override val argsSerializer: KSerializer<RunRoleNameArgs> = RunRoleNameArgs.serializer()

    override fun process(context: ArchitectureProcessContext<RunRoleNameArgs>): Result<Unit> = runCatching { }
}

private object RanFlag {
    var ran: Boolean = false
}

private object RecordsRunProcessor : ArchitectureProcessorNoArg<Unit> {
    override fun process(context: ArchitectureProcessContext<Unit>): Result<Unit> = runCatching {
        RanFlag.ran = true
    }
}

private object FilesReadingProcessorA : ArchitectureProcessorNoArg<List<String>> {
    override fun process(context: ArchitectureProcessContext<Unit>): Result<List<String>> =
        runCatching { context.roles.flatMap { context.filesOf(it) } }
}

private object FilesReadingProcessorB : ArchitectureProcessorNoArg<List<String>> {
    override fun process(context: ArchitectureProcessContext<Unit>): Result<List<String>> =
        runCatching { context.roles.flatMap { context.filesOf(it) } }
}


/** A processor whose vocabulary is not in its `Args`, the way a template's parameters are not. */
private object UndeclaredNameRunProcessor : ArchitectureProcessorNoArg<Unit> {
    override fun process(context: ArchitectureProcessContext<Unit>): Result<Unit> = runCatching { }

    override fun undeclaredArgNames(context: ArchitectureProcessContext<*>): Set<String> =
        setOf("greeting")
}
