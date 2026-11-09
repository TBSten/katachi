package me.tbsten.katachi.test.konsist.declarations

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

/** Enum entries, secondary constructors and `init` blocks: members like any other, denied unless a line allows them. */

private fun kt(body: String): String = "package com.example\n\n" + body.trimIndent() + "\n"

class KtFileContentClassMemberSpec : FreeSpec({
    "enum の項目" - {
        "allow(entry, ...) で書いた項目だけ通る" {
            judged(kt("enum class Color { RED, GREEN, blue }")) {
                `package`("**")
                allow(enum, `class`, "Color") { allow(entry, "{RED,GREEN}") }
            } shouldBe listOf("Color.blue (line 3) | Not allowed: entry blue in enum class Color")
        }

        "行が無ければ項目は許されない" {
            judged(kt("enum class Color { RED }")) {
                `package`("**")
                allow(enum, `class`, "Color")
            } shouldBe listOf("Color.RED (line 3) | Not allowed: entry RED in enum class Color")
        }

        "allow(anythingElse) は項目も許す" {
            judged(kt("enum class Color { RED }")) {
                `package`("**")
                allow(enum, `class`, "Color") { allow(anythingElse) }
            }.shouldBeEmpty()
        }

        "required(entry, ...) が無ければ足りないと出す" {
            judged(kt("enum class Type { A }")) {
                `package`("**")
                allow(enum, `class`, "Type") {
                    required(entry, "UNKNOWN")
                    allow(entry, "*")
                }
            } shouldBe listOf("Type.UNKNOWN | Missing: entry UNKNOWN in enum class Type")
        }
    }

    "副コンストラクタと init" - {
        "allow(private, constructor)(...) は書いた引数の private な副コンストラクタを許す" {
            judged(kt("class A(val id: Int) {\n    private constructor(text: String) : this(text.length)\n    init { }\n}")) {
                `package`("**")
                allow(`class`, "A") {
                    allow(`val`, "id")
                    allow(private, constructor)("text: String")
                    allow(init)
                }
            }.shouldBeEmpty()
        }

        "行が無ければ副コンストラクタと init は許されない" {
            judged(kt("class A(val id: Int) {\n    constructor(text: String) : this(text.length)\n    init { }\n}")) {
                `package`("**")
                allow(`class`, "A") { allow(`val`, "id") }
            } shouldBe listOf(
                "A.constructor (line 4) | Not allowed: constructor(text: String) in class A",
                "A.init (line 5) | Not allowed: init in class A",
            )
        }

        "修飾子を 2 つ並べて、注釈付きの private な副コンストラクタを求められる" {
            val withInject = kt("class A(val id: Int) {\n    @Inject private constructor(text: String) : this(text.length)\n}")
            judged(withInject) {
                `package`("**")
                allow(`class`, "A") {
                    allow(`val`, "id")
                    required(annotated("Inject"), private, constructor)("text: String")
                }
            }.shouldBeEmpty()

            val withoutInject = kt("class A(val id: Int) {\n    private constructor(text: String) : this(text.length)\n}")
            judged(withoutInject) {
                `package`("**")
                allow(`class`, "A") {
                    allow(`val`, "id")
                    required(annotated("Inject"), private, constructor)("text: String")
                }
            }.joinToString("\n") shouldContain "Missing:"
        }
    }
})
