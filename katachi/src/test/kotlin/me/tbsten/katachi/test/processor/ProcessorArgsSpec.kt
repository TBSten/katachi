package me.tbsten.katachi.test.processor

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.processor.ArchitectureProcessContext
import me.tbsten.katachi.processor.ArchitectureProcessNoArgContext
import me.tbsten.katachi.processor.ArchitectureProcessor
import me.tbsten.katachi.processor.ArchitectureProcessorNoArg
import me.tbsten.katachi.processor.internal.FakeArchitectureProcessContext
import me.tbsten.katachi.processor.KatachiInvalidProcessorArgException
import me.tbsten.katachi.processor.KatachiUnknownProcessorArgException
import me.tbsten.katachi.processor.KatachiUnsupportedProcessorArgException
import me.tbsten.katachi.processor.decodeFromStringMap
import me.tbsten.katachi.processor.internal.checkNoUnknownArgs
import me.tbsten.katachi.test.dsl.files.ForbiddenFileSystem

class ProcessorArgsSpec : FreeSpec({
    "decodeFromStringMap" - {
        "@Serializable な Args が Map<String, String> からデコードできる" {
            decodeFromStringMap(
                BasicArgs.serializer(),
                mapOf("name" to "GetUser", "count" to "2", "dryRun" to "true", "kind" to "B"),
            ) shouldBe BasicArgs(name = "GetUser", count = 2, dryRun = true, kind = Kind.B)
        }

        "enum が読めないときは、受け付ける綴りを並べる" {
            // 大文字小文字は合わせてもらう。合わせ先が書いていないのが問題だった。
            val thrown = shouldThrow<KatachiInvalidProcessorArgException> {
                decodeFromStringMap(BasicArgs.serializer(), mapOf("name" to "X", "kind" to "b"))
            }

            thrown.message.shouldNotBeNull() shouldContain "Accepted values: A, B."
        }

        "値にカンマが入っていても String で受ければ分割されない" {
            decodeFromStringMap(StringArgs.serializer(), mapOf("value" to "a,b")) shouldBe
                StringArgs("a,b")
        }

        "List<String> で受けるとカンマで分割される" {
            decodeFromStringMap(ListArgs.serializer(), mapOf("tags" to "a,b")).tags shouldBe
                listOf("a", "b")
        }

        "Set<String> で受けるとカンマで分割される" {
            decodeFromStringMap(SetArgs.serializer(), mapOf("tags" to "a,b")).tags shouldBe
                setOf("a", "b")
        }

        "List<Int> の要素も型どおりに読まれる" {
            decodeFromStringMap(IntListArgs.serializer(), mapOf("values" to "1,2,3")).values shouldBe
                listOf(1, 2, 3)
        }

        "key= は空のコレクションになる" {
            decodeFromStringMap(ListArgs.serializer(), mapOf("tags" to "")).tags shouldBe emptyList()
        }

        "省略したフィールドはデフォルト値になる" {
            decodeFromStringMap(BasicArgs.serializer(), mapOf("name" to "GetUser")) shouldBe
                BasicArgs(name = "GetUser")
        }

        "デフォルト値の無いフィールドを省略すると、何が足りないか分かるエラーになる" {
            val thrown = shouldThrow<KatachiInvalidProcessorArgException> {
                decodeFromStringMap(RequiredArgs.serializer(), emptyMap())
            }

            thrown.message.shouldNotBeNull() shouldContain "roleName"
        }

        "数値として読めない値は KatachiInvalidProcessorArgException になる" {
            shouldThrow<KatachiInvalidProcessorArgException> {
                decodeFromStringMap(IntArgs.serializer(), mapOf("count" to "abc"))
            }
        }

        "Map 型のフィールドを持つ Args は KatachiUnsupportedProcessorArgException になる" {
            val thrown = shouldThrow<KatachiUnsupportedProcessorArgException> {
                decodeFromStringMap(MapArgs.serializer(), mapOf("labels" to "a=1"))
            }

            thrown.message.shouldNotBeNull() shouldContain "Map"
        }

        "ネストした @Serializable を持つ Args も KatachiUnsupportedProcessorArgException になる" {
            // "inner" というキーがあって初めて decodeElementIndex がその要素を見つけ、
            // beginStructure がネストした構造として呼ばれる。無ければデフォルト値
            // Inner() が使われ、ネストの分岐を一切通らない。
            shouldThrow<KatachiUnsupportedProcessorArgException> {
                decodeFromStringMap(NestedArgs.serializer(), mapOf("name" to "X", "inner" to "y"))
            }
        }

        "別の processor 宛てのキーが混ざっていても decodeFromStringMap は落ちない" {
            decodeFromStringMap(
                BasicArgs.serializer(),
                mapOf("name" to "GetUser", "somethingElse" to "ignored"),
            ) shouldBe BasicArgs(name = "GetUser")
        }

        "Unit.serializer() を --arg 無しでデコードすると Unit になる（ArchitectureProcessorNoArg の経路）" {
            decodeFromStringMap(Unit.serializer(), emptyMap()) shouldBe Unit
        }

        "Unit.serializer() は他の processor 宛てのキーが混ざっていても素通りする" {
            // 未知キー判定は checkNoUnknownArgs の仕事であって、decodeFromStringMap は関知しない。
            decodeFromStringMap(Unit.serializer(), mapOf("roleName" to "GetUser")) shouldBe Unit
        }
    }

    "checkNoUnknownArgs" - {
        "未知キーは選ばれた processor 全部の既知キーの和集合に対して判定される" {
            // `--processor=docs,template --arg roleName=X` の再現: docs は引数を取らず、
            // template だけが roleName を知っている。
            check(
                selected = listOf("docs" to NoArgProcessor, "template" to RoleNameProcessor),
                values = mapOf("roleName" to "X"),
            )
        }

        "どの processor も知らないキーは既知キーの一覧つきで落ちる" {
            val thrown = shouldThrow<KatachiUnknownProcessorArgException> {
                check(
                    selected = listOf("template" to RoleNameProcessor),
                    values = mapOf("roleNam" to "X"),
                )
            }

            thrown.unknown shouldContain "roleNam"
            thrown.known shouldContain "roleName"
        }

        "processor が undeclaredArgNames で答えた名前は、表明なしに通る" {
            // 表明の仕組みが無くなったので、答えはそのまま run が受け付ける集合になる。
            check(
                selected = listOf("scaffold" to UndeclaredNameProcessor),
                values = mapOf("greeting" to "hi"),
            )
        }

        "processor が答えなかった名前は、これまでどおり通らない" {
            // 「何でも受け取る」ではない、というのがこの設計の要。
            val thrown = shouldThrow<KatachiUnknownProcessorArgException> {
                check(
                    selected = listOf("scaffold" to UndeclaredNameProcessor),
                    values = mapOf("greetingg" to "hi"),
                )
            }

            thrown.unknown shouldBe setOf("greetingg")
        }

        "processor の undeclaredArgNames が答えられなければ、その例外がそのまま出る" {
            // 選ばれた processor 全部に網を張らずに問い合わせる: 答えられなければ run 自体が失敗する。
            shouldThrow<IllegalStateException> {
                check(
                    selected = listOf("broken" to ThrowingUndeclaredNameProcessor),
                    values = mapOf("anything" to "x"),
                )
            }
        }

        "役割ごとの引数を名乗る processor は、その名前を通す" {
            // undeclaredArgNames の KDoc に載っている例そのもの。
            check(
                selected = listOf("annotate" to AnnotateRoles),
                values = mapOf("domain.UseCase" to "owned by the platform team"),
                architecture = oneRoleArchitecture(),
            )

            withClue("役割名そのものを返す実装だと、本物のキーのほうが誤字と呼ばれる") {
                shouldThrow<KatachiUnknownProcessorArgException> {
                    check(
                        selected = listOf("annotate" to AnnotateRoles),
                        values = mapOf("domain.Nope" to "x"),
                        architecture = oneRoleArchitecture(),
                    )
                }.unknown shouldBe setOf("domain.Nope")
            }
        }

        "docs,template を同時に選んでも通る -- template のパラメータは表明なしで通る" {
            check(
                selected = listOf("docs" to NoArgProcessor, "template" to UndeclaredNameProcessor),
                values = mapOf("greeting" to "hi"),
            )
        }
    }
})

/** Runs `checkNoUnknownArgs` against a context that refuses to be read. */
private fun check(
    selected: List<Pair<String, ArchitectureProcessor<*, *>>>,
    values: Map<String, String>,
    architecture: Architecture = architecture { },
) = checkNoUnknownArgs(
    selected = selected,
    // ForbiddenFileSystem: the check runs before any processor does, so answering "is this key a
    // typo" may not walk the project. A context that throws on every read is what holds that.
    context = FakeArchitectureProcessContext(
        architecture = architecture,
        args = Unit,
        fileSystem = ForbiddenFileSystem,
        rawArgs = values,
    ),
    values = values,
)

@Serializable
private enum class Kind { A, B }

@Serializable
private data class BasicArgs(
    val name: String,
    val count: Int = 1,
    val dryRun: Boolean = false,
    val kind: Kind = Kind.A,
)

@Serializable
private data class StringArgs(val value: String)

@Serializable
private data class ListArgs(val tags: List<String> = emptyList())

@Serializable
private data class SetArgs(val tags: Set<String> = emptySet())

@Serializable
private data class IntListArgs(val values: List<Int> = emptyList())

@Serializable
private data class RequiredArgs(val roleName: String)

@Serializable
private data class IntArgs(val count: Int)

@Serializable
private data class MapArgs(val labels: Map<String, String> = emptyMap())

@Serializable
private data class Inner(val value: String = "")

@Serializable
private data class NestedArgs(val name: String, val inner: Inner = Inner())

@Serializable
private data class RoleNameArgs(val roleName: String)

private object NoArgProcessor : ArchitectureProcessorNoArg<Unit> {
    override fun process(context: ArchitectureProcessNoArgContext): Result<Unit> = runCatching { }
}

private object RoleNameProcessor : ArchitectureProcessor<RoleNameArgs, Unit> {
    override val argsSerializer: KSerializer<RoleNameArgs> = RoleNameArgs.serializer()

    override fun process(context: ArchitectureProcessContext<RoleNameArgs>): Result<Unit> = runCatching { }
}


/** Answers with one name that no `Args` field declares, the way a template answers with a role's. */
private object UndeclaredNameProcessor : ArchitectureProcessorNoArg<Unit> {
    override fun process(context: ArchitectureProcessNoArgContext): Result<Unit> = runCatching { }

    override fun undeclaredArgNames(context: ArchitectureProcessContext<*>): Set<String> =
        setOf("greeting")
}

/** Breaks the contract that `undeclaredArgNames` never throws, so the run can be seen surviving it. */
private object ThrowingUndeclaredNameProcessor : ArchitectureProcessorNoArg<Unit> {
    override fun process(context: ArchitectureProcessNoArgContext): Result<Unit> = runCatching { }

    override fun undeclaredArgNames(context: ArchitectureProcessContext<*>): Set<String> =
        throw IllegalStateException("undeclaredArgNames should not be trusted to behave")
}

/**
 * The example in [ArchitectureProcessor.undeclaredArgNames]'s KDoc, compiled.
 *
 * Kept here rather than only in the KDoc because that example used to return role *names* as the
 * accepted argument names, which compiles and is wrong: it would accept `--arg UseCase=...` and
 * go on calling every real key a typo. An example nobody runs drifts from the thing it explains.
 */
private object AnnotateRoles : ArchitectureProcessorNoArg<Unit> {
    override fun process(context: ArchitectureProcessNoArgContext): Result<Unit> = runCatching {
        for ((role, note) in context.rawArgs) context.log("$role: $note")
    }

    override fun undeclaredArgNames(context: ArchitectureProcessContext<*>): Set<String> =
        context.roles.map { it.qualifiedName }.toSet()
}

/** One role in one group, so `qualifiedName` is a two-part name rather than a bare one. */
private fun oneRoleArchitecture(): Architecture = architecture {
    "domain".group { "UseCase" { } }
}
