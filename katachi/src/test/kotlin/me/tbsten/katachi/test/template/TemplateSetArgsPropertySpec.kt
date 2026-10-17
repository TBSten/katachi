package me.tbsten.katachi.test.template

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlin.random.Random
import me.tbsten.katachi.dsl.KatachiMissingTemplateParameterException
import me.tbsten.katachi.dsl.template
import me.tbsten.katachi.template.KatachiInvalidTemplateSetArgException
import me.tbsten.katachi.template.KatachiMissingTemplateCaptureException
import me.tbsten.katachi.test.check.architectureOf

/** Where one `arg(...)` of a generated member gets its value. */
private sealed interface ArgSource {
    /** A fixed spelling. */
    class Const(val value: String) : ArgSource

    /** The set's own parameter of this name, passed as it is. */
    object FromSet : ArgSource

    /** The set's own parameter of this name with `X` appended, an expression built from it. */
    object Expression : ArgSource
}

/** A member role: which captures its path names and which parameters its template declares. */
private class GeneratedRole(
    val index: Int,
    val captures: List<String>,
    val hasP0: Boolean,
    val default0: String?,
    val hasP1: Boolean,
    val default1: Boolean?,
) {
    val specifier = "g.R$index"
    val directory = "r$index"
}

private class GeneratedMember(val role: GeneratedRole, val args: Map<String, ArgSource>)

/**
 * Template sets against single-template runs, for random sets.
 *
 * Seeded [Random] loops rather than `io.kotest.property`, which `:katachi` does not depend on
 * (see [TemplateCapturePlacementPropertySpec]).
 *
 * The set's own parameters are named `p0` and `c0`, the same as a parameter and a capture of
 * every member role, so that "a name that was not passed with `arg` does not reach the member" is
 * visible in the member's content: the set's values are the markers `SET-P0` / `SET-C0`, which
 * nothing else produces.
 *
 * The failing side of the generator only reaches [KatachiInvalidTemplateSetArgException.Problem.Missing]
 * (a member left without a value): an unknown name, an unreadable value and overlapping paths are
 * pinned by example in [TemplateSetGenerationSpec], not by this property.
 */
class TemplateSetArgsPropertySpec : FreeSpec({
    val iterations = System.getenv("KATACHI_PBT_ITERATIONS")?.toIntOrNull() ?: 200
    val seed = System.getenv("KATACHI_PBT_SEED")?.toLongOrNull() ?: 20261017L
    val setValues = mapOf("p0" to "SET-P0", "c0" to "SET-C0")

    fun generateRole(random: Random, index: Int) = GeneratedRole(
        index = index,
        captures = listOf("c0", "c1", "c2").shuffled(random).take(random.nextInt(0, 3)),
        hasP0 = random.nextBoolean(),
        default0 = if (random.nextBoolean()) "d0" else null,
        hasP1 = random.nextBoolean(),
        default1 = if (random.nextBoolean()) random.nextBoolean() else null,
    )

    fun generateMember(random: Random, role: GeneratedRole): GeneratedMember {
        val names = role.captures + listOfNotNull("p0".takeIf { role.hasP0 }, "p1".takeIf { role.hasP1 })
        val args = linkedMapOf<String, ArgSource>()
        for (name in names.filter { random.nextInt(3) != 0 }) {
            args[name] = when {
                name == "p1" -> ArgSource.Const(random.nextBoolean().toString())
                name == "p0" || name == "c0" -> when (random.nextInt(3)) {
                    0 -> ArgSource.Const("k$name")
                    1 -> ArgSource.FromSet
                    else -> ArgSource.Expression
                }
                else -> ArgSource.Const("k$name")
            }
        }
        return GeneratedMember(role, args)
    }

    fun valueOf(name: String, source: ArgSource): String = when (source) {
        is ArgSource.Const -> source.value
        ArgSource.FromSet -> setValues.getValue(name)
        ArgSource.Expression -> setValues.getValue(name) + "X"
    }

    "任意の組は、各メンバを arg の表だけを値に単体で生成した結果の和と一致し、どれかが失敗すれば全体が失敗する" {
        val random = Random(seed)
        var succeeded = 0
        var failed = 0
        repeat(iterations) { trial ->
            val roles = (0 until random.nextInt(1, 5)).map { generateRole(random, it) }
            val members = roles.shuffled(random).take(random.nextInt(1, roles.size + 1)).map { generateMember(random, it) }
            val arch = architectureOf {
                "g".group {
                    for (role in roles) {
                        "R${role.index}".role {
                            layout {
                                val key = role.directory + "/" + role.captures.joinToString("") { capture(it) + "/" } + "File.kt"
                                key.file().template {
                                    val parts = mutableListOf("R${role.index}")
                                    for (name in role.captures) parts += "$name=${captureValue(name)}"
                                    if (role.hasP0) {
                                        val p0 by stringParameter(default = role.default0)
                                        parts += "p0=$p0"
                                    }
                                    if (role.hasP1) {
                                        val p1 by booleanParameter(default = role.default1)
                                        parts += "p1=$p1"
                                    }
                                    parts.joinToString("|")
                                }
                            }
                        }
                    }
                    "Gen".templateSet {
                        val p0 by stringParameter()
                        val c0 by stringParameter()
                        for (member in members) {
                            template(member.role.specifier) {
                                for ((name, source) in member.args) {
                                    val base = when (name) { "p0" -> p0; "c0" -> c0; else -> "" }
                                    arg(
                                        name,
                                        when (source) {
                                            is ArgSource.Const -> source.value
                                            ArgSource.FromSet -> base
                                            ArgSource.Expression -> "${base}X"
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
            val argTables = members.map { member -> member.args.mapValues { (name, source) -> valueOf(name, source) } }
            val alone = members.indices.map { index ->
                runCatching { arch.generated(members[index].role.specifier, argTables[index]) }
            }

            withClue("trial=$trial roles=${roles.map { it.captures }} members=${members.map { it.role.index to it.args.keys }}") {
                val firstFailure = alone.indexOfFirst { it.isFailure }
                if (firstFailure < 0) {
                    succeeded++
                    // 性質 1（等価）。順も、書いた順に和を取ったものと同じ。
                    val expected = alone.fold(emptyMap<String, String>()) { all, one -> all + one.getOrThrow() }
                    val actual = arch.generated("g.Gen", setValues)
                    actual.entries.map { it.key to it.value } shouldBe expected.entries.map { it.key to it.value }
                    // 性質 3（届く範囲）: 組の出力のうち各メンバのパス(r<index>/…)の中身を見る。
                    // arg に無い名前の組の値は、同名でもメンバの中身に現れない。
                    members.forEach { member ->
                        val text = actual.entries
                            .filter { it.key.startsWith(member.role.directory + "/") }
                            .single()
                            .let { it.key + " " + it.value }
                        if ("p0" !in member.args) text shouldNotContain "SET-P0"
                        if ("c0" !in member.args) text shouldNotContain "SET-C0"
                        if (member.args["p0"] == ArgSource.FromSet || member.args["p0"] is ArgSource.Expression) text shouldContain "SET-P0"
                    }
                } else {
                    failed++
                    // 性質 2（全部か無し）: 失敗するメンバが 1 つでもあれば、map は返らず例外になる。
                    val cause = alone[firstFailure].exceptionOrNull()
                    val thrown = shouldThrow<KatachiInvalidTemplateSetArgException> { arch.generated("g.Gen", setValues) }
                    thrown.member shouldBe members[firstFailure].role.specifier
                    // 例外の種類だけが写しの関係: 値が足りなければ Missing。
                    when (cause) {
                        is KatachiMissingTemplateCaptureException, is KatachiMissingTemplateParameterException ->
                            thrown.problem shouldBe KatachiInvalidTemplateSetArgException.Problem.Missing
                        else -> throw AssertionError("unexpected failure of a single run: $cause")
                    }
                }
            }
        }
        // 生成器が成功と失敗の両方に届いていること。どちらかが 0 なら性質を確かめたことにならない。
        withClue("succeeded=$succeeded failed=$failed") {
            (succeeded > 0 && failed > 0) shouldBe true
        }
    }
})
