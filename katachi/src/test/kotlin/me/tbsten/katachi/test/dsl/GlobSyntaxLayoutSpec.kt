package me.tbsten.katachi.test.dsl

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import me.tbsten.katachi.dsl.KatachiCaptureInsideBracketsException
import me.tbsten.katachi.dsl.KatachiTemplateOnWildcardException
import me.tbsten.katachi.dsl.LayoutEntryKind
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.gradle.module
import me.tbsten.katachi.dsl.internal.PathCapture
import me.tbsten.katachi.dsl.internal.captureValuesOf
import me.tbsten.katachi.dsl.internal.fillCapturedSegments
import me.tbsten.katachi.dsl.internal.flattenLayout
import me.tbsten.katachi.dsl.template

/**
 * `?`, `[...]` and `{...}` in a layout key: what they do to a declaration's presence, to a
 * capture beside them, to a template, and to the files a constraint covers.
 *
 * NOTE: このファイルのパッケージを `me.tbsten.katachi.dsl` にしてはいけない（FileConstraintCoverageSpec と同じ理由）。
 */
class GlobSyntaxLayoutSpec : FreeSpec({
    "必須かどうか" - {
        "? [ { を含むファイルは必須にならない" {
            listOf("Foo?.kt", "[ab].kt", "{a,b}.kt").forEach { key ->
                layoutOf { key.file() }.single().required shouldBe false
            }
        }

        "エスケープした \\? \\[ \\{ だけのファイルは必須になる" {
            layoutOf { """a\?\[\{.kt""".file() }.single().required shouldBe true
        }

        "読めない { は役割と宣言位置つきで弾かれる" {
            val failure = shouldThrow<me.tbsten.katachi.dsl.KatachiGlobSyntaxException> { layoutOf { "{a".file() } }
            failure.message.shouldNotBeNull() shouldContain "never closes it"
        }
    }

    "capture と ? [...] {...}" - {
        "{} の中の capture は宣言時の例外になる" {
            val failure = shouldThrow<KatachiCaptureInsideBracketsException> {
                layoutOf { "{${capture("name")},Home}Screen.kt".file() }
            }
            failure.name shouldBe "name"
            failure.opening shouldBe '{'
            failure.message.shouldNotBeNull() shouldContain """Move the capture out, such as "${'$'}{capture("name")}{Screen,Route}"."""
        }

        "[] の中の capture は宣言時の例外になる" {
            shouldThrow<KatachiCaptureInsideBracketsException> {
                layoutOf { "[${capture("name")}].kt".file() }
            }.opening shouldBe '['
        }

        "capture が ? や {..} と隣り合っても許す" {
            val entries = layoutOf {
                "${capture("name")}?.kt".file()
                "${capture("name")}{Screen,Route}.kt".file()
                "${capture("name")}[ab].kt".file()
            }
            entries.map { it.captureVariants.single().names } shouldBe List(3) { listOf("name") }
        }

        "文字と混ざった capture は空の値で当たる" {
            val entry = layoutOf { "${capture("name")}Screen.kt".file() }.single()
            entry.glob.matches("Screen.kt") shouldBe true
            entry.captureValuesOf("Screen.kt") shouldBe mapOf("name" to "")
            entry.captureValuesOf("HomeScreen.kt") shouldBe mapOf("name" to "Home")
        }
    }

    "モジュールキー" - {
        "{..} はモジュール一覧に対して展開する" {
            val entries = layoutOf(moduleIndexOf("feature/home", "feature/settings", "feature/other")) {
                ":feature:{home,settings}".module { "A.kt".file() }
            }

            entries.map { it.path }.filter { it.endsWith("/A.kt") }.sorted() shouldBe
                listOf("feature/home/A.kt", "feature/settings/A.kt")
        }

        "[..] と * を組み合わせたキーも展開する" {
            val entries = layoutOf(moduleIndexOf("core/data", "core/ui", "core/arch")) {
                ":core:[a-d]*".module { "A.kt".file() }
            }

            entries.map { it.path }.filter { it.endsWith("/A.kt") }.sorted() shouldBe
                listOf("core/arch/A.kt", "core/data/A.kt")
        }
    }

    "capture の埋め込み" {
        val captures = listOf(PathCapture(0, 0, "x", null))
        fillCapturedSegments("*{a,*}", captures) { "v" } shouldBe listOf("v{a,*}")
    }

    "テンプレート" - {
        "? [..] {..} を含むパスへの template は KatachiTemplateOnWildcardException" {
            listOf("Foo?.kt", "[ab].kt", "{a,b}.kt").forEach { key ->
                shouldThrow<KatachiTemplateOnWildcardException> {
                    layoutOf { key.file().template { "" } }
                }
            }
        }

        "capture を付けても {} が残れば同じ例外で、文面に ? [...] {...} が入る" {
            val failure = shouldThrow<KatachiTemplateOnWildcardException> {
                layoutOf { "${capture("name")}{Screen,Route}.kt".file().template { "" } }
            }
            failure.message.shouldNotBeNull() shouldContain "or a ?, [...] or {...}"
            failure.message.shouldNotBeNull() shouldContain "and write ?, [...] and {...} out"
        }

        "{..} を含むモジュールキーの下の template も同じ例外" {
            shouldThrow<KatachiTemplateOnWildcardException> {
                architecture {
                    "feature".group {
                        "Screen".role {
                            layout { ":feature:{home,settings}".module { "A.kt".file().template { "" } } }
                        }
                    }
                }.flattenLayout()
            }
        }
    }

    "anyFile() とルート直下のファイル" - {
        fun coverageOf(key: String, rootEntry: Boolean = false) = architecture {
            "build".group {
                "Any".role {
                    layout {
                        key {
                            fileConstraint("anything", check = silent())
                            anyFile()
                        }
                    }
                }
            }
        }.declaredFileConstraints().single().coverage

        "段全体の * の anyFile はルート直下のファイルを覆わない" {
            val coverage = coverageOf("*")
            coverage.covers("Root.kt") shouldBe false
            coverage.covers("dir/File.kt") shouldBe true
        }

        "** の anyFile もルート直下のファイルを覆わない" {
            val coverage = coverageOf("**")
            coverage.covers("Root.kt") shouldBe false
            coverage.covers("a/b/File.kt") shouldBe true
        }
    }

    "ルートのエントリの種別" {
        layoutOf { "{a,b}".file() }.single().kind shouldBe LayoutEntryKind.File
    }
})
