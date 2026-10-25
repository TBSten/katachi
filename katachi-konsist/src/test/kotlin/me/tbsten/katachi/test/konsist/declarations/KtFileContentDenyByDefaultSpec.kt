package me.tbsten.katachi.test.konsist.declarations

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.check.UnsatisfiedFileConstraint
import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.konsist.declarations.ktFileContent

/**
 * Deny by default (decided 2026-10-25): `ktFileContent { }` on its own rejects the file's package
 * declaration, every import and every declaration. A kind no line is written for is an empty
 * allow list, a class line without `{ }` allows no member, and what opens a kind up is a line
 * that already exists: `` `package`("**") ``, `` `import`("**") ``, `allow(anythingElse)`, and
 * `{ allow(anythingElse) }`.
 */
class KtFileContentDenyByDefaultSpec : FreeSpec({
    "空のブロックは package 宣言・import・宣言をすべて違反にする" {
        judged("package com.example\n\nimport a.B\n\nclass Sample\n\nfun helper() {}\n") {} shouldBe listOf(
            "package com.example (line 1) | Not allowed: package com.example | Note: $NOTHING_WRITTEN",
            "import a.B (line 3) | Not allowed: ktFileContent { } allows no import here",
            "Sample (line 5) | Not allowed: class Sample",
            "helper (line 7) | Not allowed: fun helper()",
        )
    }

    "行を書いた種類だけでなく、書かなかった種類も違反にする" - {
        "宣言の行だけなら、package と import が違反" {
            judged("package com.example\n\nimport a.B\n\nclass Sample\n") { required(`class`, fileName) } shouldBe listOf(
                "package com.example (line 1) | Not allowed: package com.example",
                "import a.B (line 3) | Not allowed: ktFileContent { } allows no import here",
            )
        }

        "import の行だけなら、package と宣言が違反" {
            judged("package com.example\n\nimport a.B\n\nclass Sample\n") { `import`("a.**") } shouldBe listOf(
                "package com.example (line 1) | Not allowed: package com.example",
                "Sample (line 5) | Not allowed: class Sample",
            )
        }

        "package の行だけなら、import と宣言が違反" {
            judged("package com.example\n\nimport a.B\n\nclass Sample\n") { `package`("com.example") } shouldBe listOf(
                "import a.B (line 3) | Not allowed: ktFileContent { } allows no import here",
                "Sample (line 5) | Not allowed: class Sample",
            )
        }
    }

    "package 宣言の無いファイルは、package の行が無くても通る（宣言していないので禁止するものが無い）" {
        judged("class Sample\n") { required(`class`, fileName) }.shouldBeEmpty()
    }

    "今ある書き方で開ける" - {
        "`package`(\"**\") はどの package にも当たる" {
            for (packageName in listOf("a", "com.example", "com.example.feature.home.ui")) {
                judged("package $packageName\n\nclass Sample\n") {
                    `package`("**")
                    required(`class`, fileName)
                }.shouldBeEmpty()
            }
        }

        "`package`(\"**\") は package 宣言の無いファイルにも当たる" {
            judged("class Sample\n") {
                `package`("**")
                required(`class`, fileName)
            }.shouldBeEmpty()
        }

        "`package`(\"*\") は 1 階層なので、package 宣言の無いファイルには当たらない" {
            judged("class Sample\n") {
                `package`("*")
                required(`class`, fileName)
            } shouldBe listOf("package (none) | Not allowed: the file declares no package")
        }

        "not.`package`(\"**\") は package 宣言の無いファイルにも当たる" {
            judged("class Sample\n") {
                `package`("**")
                not.`package`("**")
                required(`class`, fileName)
            } shouldBe listOf("package (none) | Not allowed: the file declares no package | Prohibited by: not package **")
        }

        "\"\" に当たらない package の行では、package 宣言の無いファイルは今と同じく違反" {
            judged("class Sample\n") {
                `package`("com.example.**")
                required(`class`, fileName)
            } shouldBe listOf("package (none) | Not allowed: the file declares no package")
        }

        "`import`(\"**\") はどの import にも当たる（star import・alias・1 階層を含む）" {
            val source = "package com.example\n\nimport Top\nimport a.B\nimport a.b.c.*\nimport x.y.Z as Alias\n\nclass Sample\n"
            judged(source) {
                `package`("**")
                `import`("**")
                required(`class`, fileName)
            }.shouldBeEmpty()
        }

        "allow(anythingElse) はどの宣言にも当たり、そのメンバは見ない" {
            val source = "package com.example\n\n" +
                "data class Sample(val id: Int) {\n    companion object { const val X = 1 }\n    fun f() {}\n}\n\n" +
                "private suspend fun helper() {}\ntypealias Id = Int\nobject Thing { val y = 2 }\n"
            judged(source) {
                `package`("**")
                allow(anythingElse)
            }.shouldBeEmpty()
        }

        "allow(`class`, \"*\") { allow(anythingElse) } はどのメンバにも当たる" {
            val source = "package com.example\n\n" +
                "class Sample(val id: Int) {\n    companion object { const val X = 1 }\n    class Nested { fun g() {} }\n    private fun f() {}\n}\n"
            judged(source) {
                `package`("**")
                allow(`class`, "*") { allow(anythingElse) }
            }.shouldBeEmpty()
        }
    }

    "{ } の無いクラスの行は、空の { } と同じでメンバを持てない" - {
        "class の関数" {
            judged("package com.example\n\nclass Sample {\n    fun load() {}\n}\n") {
                `package`("**")
                required(`class`, fileName)
            } shouldBe listOf("Sample.load (line 4) | Not allowed: fun load() in class Sample")
        }

        "data class の主コンストラクタの val も数える" {
            judged("package com.example\n\ndata class Sample(val id: Int, var name: String)\n") {
                `package`("**")
                required(data, `class`, fileName)
            } shouldBe listOf(
                "Sample.id (line 3) | Not allowed: val id: Int in data class Sample",
                "Sample.name (line 3) | Not allowed: var name: String in data class Sample",
            )
        }

        "companion object も数える" {
            judged("package com.example\n\nclass Sample {\n    companion object\n}\n") {
                `package`("**")
                required(`class`, fileName)
            } shouldBe listOf("Sample.Companion (line 4) | Not allowed: companion object Companion in class Sample")
        }

        "object・interface・enum class も同じ" {
            val source = "package com.example\n\n" +
                "object Sample { val a = 1 }\n" +
                "interface Port { fun send() }\n" +
                "enum class Kind { A, B; fun label() = name }\n"
            judged(source) {
                `package`("**")
                required(`object`, fileName)
                required(`interface`, "Port")
                required(enum, `class`, "Kind")
            } shouldBe listOf(
                "Sample.a (line 3) | Not allowed: val a in object Sample",
                "Port.send (line 4) | Not allowed: fun send() in interface Port",
                "Kind.label (line 5) | Not allowed: fun label() in enum class Kind",
            )
        }

        "enum の項目はメンバに数えない（今と同じ）" {
            judged("package com.example\n\nenum class Sample { A, B }\n") {
                `package`("**")
                required(enum, `class`, fileName)
            }.shouldBeEmpty()
        }

        "メンバの無いクラスは通る" {
            judged("package com.example\n\nclass Sample\n\nclass Other()\n") {
                `package`("**")
                required(`class`, fileName)
                allow(`class`, "Other")
            }.shouldBeEmpty()
        }

        "入れ子のクラスの行に { } が無ければ、入れ子のメンバも持てない" {
            judged("package com.example\n\nclass Sample {\n    class Nested {\n        fun g() {}\n    }\n}\n") {
                `package`("**")
                required(`class`, fileName) {
                    allow(`class`, "Nested")
                }
            } shouldBe listOf("Sample.Nested.g (line 5) | Not allowed: fun g() in class Nested")
        }

        "{ } の無い行と allow(anythingElse) の両方に当たれば、行が先に当たるのでメンバを持てない" {
            judged("package com.example\n\nclass Sample {\n    fun load() {}\n}\n") {
                `package`("**")
                allow(`class`, "*")
                allow(anythingElse)
            } shouldBe listOf("Sample.load (line 4) | Not allowed: fun load() in class Sample")
        }

        "当たる行が複数あれば、{ } を持つ最初の行でメンバを判定する" {
            judged("package com.example\n\nclass Sample {\n    fun load() {}\n}\n") {
                `package`("**")
                allow(`class`, "*")
                required(`class`, fileName) { allow(`fun`, "load")() }
            }.shouldBeEmpty()
        }
    }

    "not.allow だけを書いた一覧は、今と同じく禁止に当たらないものも違反" {
        judged("package com.example\n\nclass Sample\nclass LegacyImpl\n") {
            `package`("**")
            not.allow(`class`, "*Impl")
        } shouldBe listOf(
            "Sample (line 3) | Not allowed: class Sample",
            "LegacyImpl (line 4) | Not allowed: class LegacyImpl | Prohibited by: not class *Impl",
        )
    }

    "行を書かなかった種類の違反は、開く書き方を最初の直し方に出す" - {
        "import" {
            fixLinesOf("package com.example\n\nimport a.B\n\nclass Sample\n") {
                `package`("**")
                required(`class`, fileName)
            }.first() shouldBe "import a.B: No import line is written, so every import is rejected. " +
                "To allow any import, add at $SPEC_SITE: / `import`(\"**\")"
        }

        "package" {
            fixLinesOf("package com.example\n\nclass Sample\n") { required(`class`, fileName) } shouldBe listOf(
                "package com.example: No package line is written, so no package may be declared. " +
                    "To allow any package, add at $SPEC_SITE: / `package`(\"**\")",
                "package com.example: Allow it in ktFileContent { } at $SPEC_SITE: / `package`(\"com.example\")",
                "package com.example: Or allow the package each file's directory names, at $SPEC_SITE: / `package`(matchingDirectory)",
            )
        }

        "ファイル直下の宣言" {
            fixLinesOf("package com.example\n\nclass Sample\n") { `package`("**") }.first() shouldBe
                "Sample: No declaration line is written, so every declaration is rejected. " +
                "To allow any, add at $SPEC_SITE: / allow(anythingElse)"
        }

        "{ } の無いクラスの行のメンバ" {
            var line: DeclarationSite? = null
            val fixes = fixLinesOf("package com.example\n\nclass Sample {\n    val count = 0\n}\n") {
                `package`("**")
                line = DeclarationSite.capture(); required(`class`, fileName)
            }
            fixes.first() shouldBe "Sample.count: The line at $line has no { }, so class Sample may have no members. " +
                "To allow any, give it: / { allow(anythingElse) }"
            fixes shouldContain "Sample.count: Allow it in the { } of the line at $line, giving the line one if it has none: / " +
                "allow(`val`, \"count\")"
        }

        "空の { } のメンバ" {
            var line: DeclarationSite? = null
            fixLinesOf("package com.example\n\nclass Sample {\n    val count = 0\n}\n") {
                `package`("**")
                line = DeclarationSite.capture(); required(`class`, fileName) {}
            }.first() shouldBe "Sample.count: No line is written in the { } at $line, so every member is rejected. " +
                "To allow any, add there: / allow(anythingElse)"
        }

        "行を書いた種類には出さない" {
            fixLinesOf("package com.example\n\nimport a.B\n\nclass Sample\nclass Extra\n") {
                `package`("com.other")
                `import`("kotlin.**")
                required(`class`, fileName)
            }.none { "No " in it && "line is written" in it } shouldBe true
        }
    }

    "違反の量: 開き方は一覧ごとに 1 回、最初の違反にだけ出す" - {
        "import の行が無ければ、2 つ目からの import には開き方を出さない" {
            val fixes = fixLinesOf("package com.example\n\nimport a.B\nimport c.D\n\nclass Sample\n") {
                `package`("**")
                required(`class`, fileName)
            }
            fixes.count { "No import line is written" in it } shouldBe 1
            fixes.filter { it.startsWith("import c.D: ") }.map { it.substringAfter(": ").substringBefore(" at ") } shouldBe listOf(
                "Remove the import and receive what it needs from outside",
                "Allow it in ktFileContent { }",
            )
        }

        "{ } の無い行のメンバは、開き方を 1 回だけ出し、どのメンバも同じ { } に貼れる 1 行を出す" {
            val fixes = fixLinesOf("package com.example\n\ndata class Sample(val a: Int, val b: Int)\n") {
                `package`("**")
                required(data, `class`, fileName)
            }
            fixes.count { "has no { }" in it } shouldBe 1
            fixes.filter { "giving the line one" in it }.map { it.substringAfterLast(" / ") } shouldBe listOf(
                "allow(`val`, \"a\")",
                "allow(`val`, \"b\")",
            )
        }

        "主コンストラクタのプロパティには「クラスの外へ動かす」を出さない" {
            fixLinesOf("package com.example\n\ndata class Sample(val a: Int) {\n    val b = 1\n}\n") {
                `package`("**")
                required(data, `class`, fileName)
            }.filter { "Move it out of" in it } shouldBe listOf("Sample.b: Move it out of data class Sample")
        }
    }

    "条件で行を 1 つも書かれなかったファイルは、最初の違反にそう添える" {
        val violations = ktFileContentRun(
            "src/Home.kt" to "package com.example\n\nclass Home\n",
            paths = { "src" / "*".ktFile() },
        ) {
            ktFileContent { if (fileName == "Other") required(`class`, fileName) }
        }
        val notes = violations.filterIsInstance<UnsatisfiedFileConstraint>().map { violation ->
            violation.reasons.filter { it.label == "Note" }.map { it.text }
        }
        notes shouldBe listOf(listOf(NOTHING_WRITTEN), emptyList())
    }

    "宣言した package がディレクトリと一致すれば、matchingDirectory も直し方に出す" - {
        "package の行が無いとき" {
            fixLinesOf("package com.example\n\nclass Sample\n") { required(`class`, fileName) } shouldContain
                "package com.example: Or allow the package each file's directory names, at $SPEC_SITE: / `package`(matchingDirectory)"
        }

        "package の行が当たらないとき" {
            fixLinesOf("package com.example\n\nclass Sample\n") {
                `package`("org.other")
                required(`class`, fileName)
            }.filter { "matchingDirectory" in it }.size shouldBe 1
        }

        "ディレクトリと違う package には出さない" {
            fixLinesOf("package com.other\n\nclass Sample\n") { required(`class`, fileName) }
                .none { "matchingDirectory" in it } shouldBe true
        }
    }
})
