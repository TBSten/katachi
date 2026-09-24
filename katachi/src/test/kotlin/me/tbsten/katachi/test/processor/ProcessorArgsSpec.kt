package me.tbsten.katachi.test.processor

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import me.tbsten.katachi.processor.ArchitectureProcessContext
import me.tbsten.katachi.processor.ArchitectureProcessor
import me.tbsten.katachi.processor.ArchitectureProcessorNoArg
import me.tbsten.katachi.processor.KatachiInvalidProcessorArgException
import me.tbsten.katachi.processor.KatachiUnknownProcessorArgException
import me.tbsten.katachi.processor.KatachiUnsupportedProcessorArgException
import me.tbsten.katachi.processor.checkNoUnknownArgs
import me.tbsten.katachi.processor.decodeFromStringMap

class ProcessorArgsSpec : FreeSpec({
    "decodeFromStringMap" - {
        "@Serializable な Args が Map<String, String> からデコードできる" {
            decodeFromStringMap(
                BasicArgs.serializer(),
                mapOf("name" to "GetUser", "count" to "2", "dryRun" to "true", "kind" to "B"),
            ) shouldBe BasicArgs(name = "GetUser", count = 2, dryRun = true, kind = Kind.B)
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
            checkNoUnknownArgs(
                listOf(NoArgProcessor, RoleNameProcessor),
                mapOf("roleName" to "X"),
            )
        }

        "どの processor も知らないキーは既知キーの一覧つきで落ちる" {
            val thrown = shouldThrow<KatachiUnknownProcessorArgException> {
                checkNoUnknownArgs(listOf(RoleNameProcessor), mapOf("roleNam" to "X"))
            }

            thrown.unknown shouldContain "roleNam"
            thrown.known shouldContain "roleName"
        }
    }
})

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
    override fun process(context: ArchitectureProcessContext<Unit>) {}
}

private object RoleNameProcessor : ArchitectureProcessor<RoleNameArgs, Unit> {
    override val argsSerializer: KSerializer<RoleNameArgs> = RoleNameArgs.serializer()

    override fun process(context: ArchitectureProcessContext<RoleNameArgs>) {}
}
