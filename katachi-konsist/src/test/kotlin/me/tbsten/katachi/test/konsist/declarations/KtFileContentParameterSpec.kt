@file:Suppress("DEPRECATION")

package me.tbsten.katachi.test.konsist.declarations

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.gradle.div
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.konsist.declarations.KatachiKtFileContentLineException
import me.tbsten.katachi.konsist.declarations.KatachiKtFileContentPatternException
import me.tbsten.katachi.konsist.declarations.KtFileContentLineProblem
import me.tbsten.katachi.konsist.declarations.KtFileContentPatternKind
import me.tbsten.katachi.konsist.declarations.KtFileContentScope
import me.tbsten.katachi.konsist.declarations.internal.renderRule
import me.tbsten.katachi.konsist.declarations.ktFileContent

/** The parameters of a `fun` line, written `"name: Type"`: how they match, what refuses them, how they are shown. */

private fun kt(body: String): String = "package com.example\n\n" + body.trimIndent() + "\n"

private fun lineErrorOf(block: KtFileContentScope.() -> Unit): KatachiKtFileContentLineException =
    shouldThrow<KatachiKtFileContentLineException> { rulesOf(block = block) }

class KtFileContentParameterSpec : FreeSpec({
    "一致" - {
        "名前と型の両方が当たれば通る" {
            judged(kt("fun save(name: String, count: Int) {}")) {
                allow(`fun`, "save")("name: String", "count: Int")
            }.shouldBeEmpty()
        }

        "名前だけ違えば当たらず、実際の引数を Kotlin の書き方で出す" {
            judged(kt("fun save(title: String) {}")) { allow(`fun`, "save")("name: String") } shouldBe listOf(
                "save (line 3) | Not allowed: fun save(title: String) | Expected: fun save(name: String) | " +
                    "Differs in: parameters (are (title: String), expected (name: String))",
            )
        }

        "型だけ違っても当たらない" {
            judged(kt("fun save(name: Int) {}")) { allow(`fun`, "save")("name: String") } shouldBe listOf(
                "save (line 3) | Not allowed: fun save(name: Int) | Expected: fun save(name: String) | " +
                    "Differs in: parameters (are (name: Int), expected (name: String))",
            )
        }

        "名前のパターン *Id は Id で終わる名前に当たる（* のある行は任意なので、外れた宣言は許可されないとだけ出る）" {
            judged(kt("fun find(userId: String, groupId: String) {}\nfun findBy(user: String) {}")) {
                allow(`fun`, "find*")("*Id: String")
                allow(`fun`, "find")("*Id: String", "*Id: String")
            } shouldBe listOf(
                "findBy (line 4) | Not allowed: fun findBy(user: String)",
            )
        }

        "id: * は名前だけを問い、型は何でもよい" {
            judged(kt("fun onA(id: Int) {}\nfun onB(id: List<String>) {}\nfun onC(key: Int) {}")) {
                allow(`fun`, "on*")("id: *")
            } shouldBe listOf(
                "onC (line 5) | Not allowed: fun onC(key: Int)",
            )
        }

        "*: String は型だけを問い、名前は何でもよい" {
            judged(kt("fun onA(text: String) {}\nfun onB(value: String) {}\nfun onC(value: Int) {}")) {
                allow(`fun`, "on*")("*: String")
            } shouldBe listOf(
                "onC (line 5) | Not allowed: fun onC(value: Int)",
            )
        }

        "並びが違えば当たらない" {
            judged(kt("fun save(count: Int, name: String) {}")) {
                allow(`fun`, "save")("name: String", "count: Int")
            } shouldBe listOf(
                "save (line 3) | Not allowed: fun save(count: Int, name: String) | Expected: fun save(name: String, count: Int) | " +
                    "Differs in: parameters (are (count: Int, name: String), expected (name: String, count: Int))",
            )
        }

        "数が多くても少なくても当たらない" {
            judged(kt("fun one(a: Int) {}\nfun three(a: Int, b: Int, c: Int) {}")) {
                allow(`fun`, "one")("a: Int", "b: Int")
                allow(`fun`, "three")("a: Int", "b: Int")
            } shouldBe listOf(
                "one (line 3) | Not allowed: fun one(a: Int) | Expected: fun one(a: Int, b: Int) | " +
                    "Differs in: parameters (are (a: Int), expected (a: Int, b: Int))",
                "three (line 4) | Not allowed: fun three(a: Int, b: Int, c: Int) | Expected: fun three(a: Int, b: Int) | " +
                    "Differs in: parameters (are (a: Int, b: Int, c: Int), expected (a: Int, b: Int))",
            )
        }

        "コロンの前後の空白は問わない" {
            judged(kt("fun save(name: String) {}\nfun load(name: String) {}\nfun drop(name: String) {}")) {
                allow(`fun`, "save")("name:String")
                allow(`fun`, "load")(" name : String ")
                allow(`fun`, "drop")("name  :  String")
            }.shouldBeEmpty()
        }

        "最初の : で分け、型の中の : は型の一部として読む" {
            judged(kt("fun on(block: (String) -> Unit, map: Map<String, Int>, callback: (result: String) -> Unit) {}")) {
                allow(`fun`, "on")("block: (String) -> Unit", "map: Map<String, *>", "callback: (result: String) -> Unit")
            }.shouldBeEmpty()
            rulesOf { allow(`fun`, "on")("callback: (result: String) -> Unit") }.firstDeclaration.parameters?.single()?.let {
                it.name.written shouldBe "callback"
                it.type.written shouldBe "(result: String) -> Unit"
            }
        }

        "バッククォートで書いた引数名は、バッククォート無しの名前で比べる" {
            judged(kt("fun on(`in`: String) {}")) { allow(`fun`, "on")("in: String") }.shouldBeEmpty()
        }
    }

    "定義を読んだ時点の誤り" - {
        ": の無い文字列は ParameterWithoutName で、名前としても型としても直し方を出す" {
            lineErrorOf { allow(`fun`, "save")("String", "count: Int") }.let {
                it.problem shouldBe KtFileContentLineProblem.ParameterWithoutName
                it.parameter shouldBe "String"
                it.line shouldBe "fun save(String, count: Int)"
                it.message shouldBe """
                    Parameter "String" of line "fun save(String, count: Int)" at ${it.declaredAt} is not written as "name: Type".
                    A parameter is written the way Kotlin writes it, "name: Type": a name pattern and a type pattern, split at the first ':'.
                    Write "String: Type" if "String" is the name, or "name: String" if it is the type; "*: String" accepts any name.
                """.trimIndent()
            }
            lineErrorOf { allow(`fun`, "find")("id") }.message.orEmpty().lines().last() shouldBe
                "Write \"id: Type\" if \"id\" is the name, or \"name: id\" if it is the type; \"*: id\" accepts any name."
        }

        "名前が空なら ParameterWithoutName" {
            lineErrorOf { allow(`fun`, "save")(": Int") }.let {
                it.problem shouldBe KtFileContentLineProblem.ParameterWithoutName
                it.parameter shouldBe ": Int"
                it.message.orEmpty().lines().first() shouldBe
                    "Parameter \": Int\" of line \"fun save(: Int)\" at ${it.declaredAt} has no name before its ':'."
                it.message.orEmpty().lines().last() shouldBe
                    "Write \"name: Int\" with the parameter's name, or \"*: Int\" to accept any name."
            }
        }

        "型が空なら ParameterWithoutType" {
            listOf("id:", "id: ", " id :").forEach { written ->
                lineErrorOf { allow(`fun`, "save")(written) }.let {
                    it.problem shouldBe KtFileContentLineProblem.ParameterWithoutType
                    it.parameter shouldBe written.trim()
                    it.message.orEmpty().lines().last() shouldBe
                        "Write \"id: String\" with the parameter's type, or \"id: *\" to accept any type."
                }
            }
        }

        "空・空白だけの文字列は ParameterWithoutName で、空の引数と言う" {
            listOf("", "   ").forEach { written ->
                lineErrorOf { allow(`fun`, "on")(written) }.let {
                    it.problem shouldBe KtFileContentLineProblem.ParameterWithoutName
                    it.message.orEmpty().lines().first() shouldBe "Line \"fun on()\" at ${it.declaredAt} has a blank parameter."
                    it.message.orEmpty().lines().last() shouldBe "Write it as \"name: Type\", such as \"name: String\", or remove it."
                }
            }
        }

        "禁止の行でも同じに断る" {
            lineErrorOf { not.allow(`fun`, "*")("Context") }.let {
                it.problem shouldBe KtFileContentLineProblem.ParameterWithoutName
                it.line shouldBe "not fun *(Context)"
            }
        }

        "文面は ASCII だけ" {
            listOf(
                lineErrorOf { allow(`fun`, "save")("String") },
                lineErrorOf { allow(`fun`, "save")("id:") },
                lineErrorOf { allow(`fun`, "save")(": Int") },
                lineErrorOf { allow(`fun`, "save")(" ") },
            ).forEach { error -> error.message.orEmpty().filter { it.code > 127 } shouldBe "" }
        }

        "architecture { } を読んだ時点で落ちる" {
            shouldThrow<KatachiKtFileContentLineException> {
                architecture {
                    "Controller".role {
                        layout { "src" / "*Controller".ktFile() }
                        ktFileContent { allow(`fun`, "register")("Route") }
                    }
                }
            }.problem shouldBe KtFileContentLineProblem.ParameterWithoutName
        }

        "名前のパターンが読めなければ ParameterName の誤りで、名前の部分だけを出す" {
            shouldThrow<KatachiKtFileContentPatternException> { rulesOf { allow(`fun`, "x")("[z-a]: String") } }.let {
                it.what shouldBe KtFileContentPatternKind.ParameterName
                it.pattern shouldBe "[z-a]"
            }
        }

        "型のパターンが読めなければ Type の誤りで、型の部分だけを出す" {
            shouldThrow<KatachiKtFileContentPatternException> { rulesOf { allow(`fun`, "x")("id: Map<{K, V>") } }.let {
                it.what shouldBe KtFileContentPatternKind.Type
                it.pattern shouldBe "Map<{K, V>"
            }
        }
    }

    "出力" - {
        "規則の描画は書いたとおりの name: Type" {
            rulesOf {
                allow(`fun`, "save")("name: String", "*: Int")
                allow(`fun`, "load")("name:String")
                not.allow(`fun`, "*")("*: Context")
            }.declarations?.lines.orEmpty().map(::renderRule) shouldBe listOf(
                "fun save(name: String, *: Int)",
                "fun load(name:String)",
                "not fun *(*: Context)",
            )
        }

        "役割ページの表も書いたとおり" {
            val page = rolePageOf {
                "Controller".role {
                    example("HealthController")
                    layout { "web" / "*Controller".ktFile() }
                    ktFileContent {
                        allow(`class`, fileName) {
                            allow(`fun`, "register")("route: Route")
                            not.allow(`fun`, "*")("*: Context")
                        }
                    }
                }
            }.getValue("Controller.md")

            page shouldContain """
                | Member | |
                |---|---|
                | `fun register(route: Route)` | required |
                | `fun *(*: Context)` | not allowed |
            """.trimIndent()
        }

        "違反の出力は実際の引数を (route: Route) の形で出す" {
            val violations = ktFileContentRun(
                "src/HealthController.kt" to kt("class HealthController {\n    fun register(path: Route) {}\n}"),
                paths = { "src" / "*Controller".ktFile() },
            ) {
                ktFileContent { allow(`class`, fileName) { allow(`fun`, "register")("route: Route") } }
            }

            violations.unsatisfiedLines() shouldBe listOf(
                "src/HealthController.kt: HealthController.register (line 4) | " +
                    "Not allowed: fun register(path: Route) in class HealthController | Expected: fun register(route: Route) | " +
                    "Differs in: parameters (are (path: Route), expected (route: Route))",
            )
        }
    }
})
