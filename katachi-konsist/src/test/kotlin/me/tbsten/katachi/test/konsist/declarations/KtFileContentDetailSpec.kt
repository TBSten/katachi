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
                allow(`val`, "name", "String")
            }.shouldBeEmpty()
        }

        "プロパティの context parameters も読む" {
            judged(kt("class Logger\ncontext(logger: Logger) val name: String get() = \"\"")) {
                `package`("**")
                allow(`class`, "Logger")
                allow(`val`, "name", "String").context("logger: Logger")
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
