package me.tbsten.katachi.test.konsist.declarations

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.konsist.declarations.KatachiKtFileContentLineException
import me.tbsten.katachi.konsist.declarations.KtFileContentLineProblem

/**
 * The calls chained after a line that ask about a detail of its declaration: context parameters, type parameters,
 * accessors, the delegate, the backing field, the primary constructor, the KDoc and the body. Each is asked about only
 * when written.
 */

private fun kt(body: String): String = "package com.example\n\n" + body.trimIndent() + "\n"

class KtFileContentDetailSpec : FreeSpec({
    "context(...)" - {
        "書いた context parameters と一致すれば通る" {
            judged(kt("class Logger\ncontext(logger: Logger) fun log() {}")) {
                `package`("**")
                allow(`class`, "Logger")
                allow(`fun`, "log")().context("logger: Logger")
            }.shouldBeEmpty()
        }

        "context parameters が無ければ違いに出す" {
            judged(kt("fun log() {}")) {
                `package`("**")
                allow(`fun`, "log")().context("*: Logger")
            } shouldBe listOf(
                "log (line 3) | Not allowed: fun log() | Expected: fun log().context(*: Logger) | " +
                    "Differs in: context parameters (are (), expected (*: Logger))",
            )
        }

        "context() は context parameters が無いことを求める" {
            judged(kt("class Logger\ncontext(logger: Logger) fun log() {}")) {
                `package`("**")
                allow(`class`, "Logger")
                allow(`fun`, "log")().context()
            } shouldBe listOf(
                "log (line 4) | Not allowed: fun log() | Expected: fun log().context() | " +
                    "Differs in: context parameters (are (logger: Logger), expected ())",
            )
        }

        "書かない行は context parameters を見ない" {
            judged(kt("class Logger\ncontext(logger: Logger) val name: String get() = \"\"")) {
                `package`("**")
                allow(`class`, "Logger")
                allow(`val`, "name").type("String")
            }.shouldBeEmpty()
        }

        "プロパティの context parameters も読む" {
            judged(kt("class Logger\ncontext(logger: Logger) val name: String get() = \"\"")) {
                `package`("**")
                allow(`class`, "Logger")
                allow(`val`, "name").type("String").context("logger: Logger")
            }.shouldBeEmpty()
        }

        "2 回書くと DetailWrittenTwice" {
            shouldThrow<KatachiKtFileContentLineException> {
                judged(kt("fun log() {}")) {
                    `package`("**")
                    allow(`fun`, "log")().context().context()
                }
            }.problem shouldBe KtFileContentLineProblem.DetailWrittenTwice
        }
    }

    "typeParameters(...)" - {
        "名前・reified・上限が当たれば通る" {
            judged(kt("inline fun <reified T : Any> get(): T = TODO()")) {
                `package`("**")
                allow(inline, `fun`, "get")().typeParameters("reified T : Any")
            }.shouldBeEmpty()
        }

        "書いていない reified は見ない" {
            judged(kt("inline fun <reified T : Any> get(): T = TODO()")) {
                `package`("**")
                allow(inline, `fun`, "get")().typeParameters("T")
            }.shouldBeEmpty()
        }

        "reified が無ければ違いに出す" {
            judged(kt("fun <T> get(): T = TODO()")) {
                `package`("**")
                allow(`fun`, "get")().typeParameters("reified T")
            } shouldBe listOf(
                "get (line 3) | Not allowed: fun get(): T | Expected: fun get().typeParameters(reified T) | " +
                    "Differs in: type parameters (are <T>, expected <reified T>)",
            )
        }

        "where に書いた上限も上限として当たる" {
            judged(kt("interface A\ninterface B\nfun <T> f(t: T) where T : A, T : B {}")) {
                `package`("**")
                allow(`interface`, "*")
                allow(`fun`, "f")(anyArgs()).typeParameters("T : B")
            }.shouldBeEmpty()
        }

        "クラスの out も読む" {
            judged(kt("class Box<out T>")) {
                `package`("**")
                allow(`class`, "Box").typeParameters("out T")
            }.shouldBeEmpty()
        }
    }

    "アクセサ・委譲・backing field" - {
        "getter() は書いた getter を求める" {
            judged(kt("val a: Int get() = 1\nval b: Int = 1")) {
                `package`("**")
                allow(`val`, "a").type("Int").getter()
                allow(`val`, "b").type("Int").getter()
            } shouldBe listOf(
                "b (line 4) | Not allowed: val b: Int | Expected: val b: Int.getter() | Differs in: getter (none written, expected one)",
            )
        }

        "setter(private) は private set を求める" {
            judged(kt("class A {\n    var a: Int = 1\n        private set\n    var b: Int = 1\n}")) {
                `package`("**")
                allow(`class`, "A") {
                    allow(`var`, "a").type("Int").setter(private)
                    allow(`var`, "b").type("Int").setter(private)
                }
            } shouldBe listOf(
                "A.b (line 6) | Not allowed: var b: Int in class A | Expected: var b: Int.setter(private) | " +
                    "Differs in: setter (none written, expected private set)",
            )
        }

        "val の行の setter() は DetailNotForKind" {
            shouldThrow<KatachiKtFileContentLineException> {
                judged(kt("val a = 1")) {
                    `package`("**")
                    allow(`val`, "a").setter()
                }
            }.problem shouldBe KtFileContentLineProblem.DetailNotForKind
        }

        "delegatedBy(\"lazy\") は by lazy を求める" {
            judged(kt("val a by lazy { 1 }\nval b = 1")) {
                `package`("**")
                allow(`val`, "a").delegatedBy("lazy")
                allow(`val`, "b").delegatedBy("lazy")
            } shouldBe listOf(
                "b (line 4) | Not allowed: val b | Expected: val b.delegatedBy(lazy) | Differs in: delegate (none, expected by lazy)",
            )
        }

        "backingField(...) は field の型を見る" {
            judged(kt("class A {\n    val items: List<String>\n        field = mutableListOf()\n    val more: List<String>\n        field: MutableList<String> = mutableListOf()\n}")) {
                `package`("**")
                allow(`class`, "A") {
                    allow(`val`, "items").type("List<String>").backingField("MutableList<*>")
                    allow(`val`, "more").type("List<String>").backingField("MutableList<*>")
                }
            } shouldBe listOf(
                "A.items (line 4) | Not allowed: val items: List<String> in class A | " +
                    "Expected: val items: List<String>.backingField(MutableList<*>) | " +
                    "Differs in: backing field (field, expected field: MutableList<*>)",
            )
        }
    }

    "primaryConstructor(...)" - {
        "private と val でない引数が当たれば通る" {
            judged(kt("class A private constructor(config: String, val id: Int)")) {
                `package`("**")
                allow(`class`, "A") { allow(`val`, "id") }.primaryConstructor(private, "config: String", "val id: Int")
            }.shouldBeEmpty()
        }

        "val と書いていない引数は val のプロパティに当たらない" {
            judged(kt("class A(val id: Int)")) {
                `package`("**")
                allow(`class`, "A") { allow(`val`, "id") }.primaryConstructor("id: Int")
            } shouldBe listOf(
                "A (line 3) | Not allowed: class A | Expected: class A.primaryConstructor(id: Int) | " +
                    "Differs in: primary constructor (is constructor(val id: Int), expected constructor(id: Int))",
            )
        }

        "書いていないコンストラクタは public で引数なし" {
            judged(kt("class A")) {
                `package`("**")
                allow(`class`, "A").primaryConstructor(public)
            }.shouldBeEmpty()
        }

        "可視性が違えば違いに出す" {
            judged(kt("class A(id: Int)")) {
                `package`("**")
                allow(`class`, "A").primaryConstructor(private, anyArgs())
            } shouldBe listOf(
                "A (line 3) | Not allowed: class A | Expected: class A.primaryConstructor(private, anyArgs()) | " +
                    "Differs in: primary constructor (is constructor(id: Int), expected private constructor(...))",
            )
        }

        "object だけの行には書けない" {
            shouldThrow<KatachiKtFileContentLineException> {
                judged(kt("object A")) {
                    `package`("**")
                    allow(`object`, "A").primaryConstructor()
                }
            }.problem shouldBe KtFileContentLineProblem.DetailNotForKind
        }

        "or の修飾子は DetailModifiers" {
            shouldThrow<KatachiKtFileContentLineException> {
                judged(kt("class A")) {
                    `package`("**")
                    allow(`class`, "A").primaryConstructor(private or internal)
                }
            }.problem shouldBe KtFileContentLineProblem.DetailModifiers
        }
    }

    "kdoc() と本体" - {
        "kdoc() は KDoc を求める" {
            judged(kt("/** Doc. */\nfun a() {}\n\nfun b() {}")) {
                `package`("**")
                allow(`fun`, "a")().kdoc()
                allow(`fun`, "b")().kdoc()
            } shouldBe listOf("b (line 6) | Not allowed: fun b() | Expected: fun b().kdoc() | Differs in: KDoc (none, expected one)")
        }

        "expressionBody() / blockBody() / noBody()" {
            judged(kt("fun a() = Unit\nfun b() {}\nabstract class C {\n    abstract fun c()\n}")) {
                `package`("**")
                allow(`fun`, "a")().expressionBody()
                allow(`fun`, "b")().expressionBody()
                allow(abstract, `class`, "C") { allow(abstract, `fun`, "c")().noBody() }
            } shouldBe listOf(
                "b (line 4) | Not allowed: fun b() | Expected: fun b().expressionBody() | " +
                    "Differs in: body (is a block body, expected an expression body)",
            )
        }
    }
})
