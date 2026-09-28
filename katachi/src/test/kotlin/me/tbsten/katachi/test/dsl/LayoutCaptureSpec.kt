package me.tbsten.katachi.test.dsl

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import me.tbsten.katachi.dsl.DeclarationKind
import me.tbsten.katachi.dsl.KatachiAdjacentCaptureException
import me.tbsten.katachi.dsl.KatachiDuplicateCaptureException
import me.tbsten.katachi.dsl.KatachiInvalidIdentifierException
import me.tbsten.katachi.dsl.KatachiStrayCaptureTokenException
import me.tbsten.katachi.dsl.LayoutEntry
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.internal.LayoutCaptures
import me.tbsten.katachi.dsl.internal.PathCapture
import me.tbsten.katachi.dsl.internal.captureToken
import me.tbsten.katachi.dsl.internal.flattenLayout
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.dsl.template
import me.tbsten.katachi.test.check.architectureOf
import me.tbsten.katachi.test.template.generated

/** The entry at [path], which the spec expects to exist exactly once. */
private fun List<LayoutEntry>.at(path: String): LayoutEntry = single { it.path == path }

class LayoutCaptureSpec : FreeSpec({
    "平坦化の結果が * と同じ" - {
        "/ の途中に書いた capture は \"*\" と同じ shape になる" {
            val named = layoutOf { "feature" / capture("feature") / "src" / "*ViewModel".ktFile() }
            val plain = layoutOf { "feature" / "*" / "src" / "*ViewModel".ktFile() }

            named.shape() shouldBe plain.shape()
            named.shape() shouldBe listOf(
                "feature [Directory]",
                "feature/* [Directory]",
                "feature/*/src [Directory]",
                "feature/*/src/*ViewModel.kt [File]",
            )
        }

        "ブロックとして書いた capture は \"*\" のブロックと同じ shape になる" {
            val named = layoutOf {
                "feature" {
                    capture("feature") {
                        description = "1 feature"
                        "README.md".file()
                    }
                }
            }
            val plain = layoutOf {
                "feature" {
                    "*" {
                        description = "1 feature"
                        "README.md".file()
                    }
                }
            }

            named.shape() shouldBe plain.shape()
        }

        "先頭に書いた capture は \"*\" と同じ shape になる" {
            layoutOf { capture("x") / "a.kt".file() }.shape() shouldBe
                layoutOf { "*" / "a.kt".file() }.shape()
        }

        "capture の下の anyFile と ignore も \"*\" と同じ shape になる" {
            val named = layoutOf {
                "generated" / capture("x") { anyFile() }
                "build" / capture("y") { ignore() }
            }
            val plain = layoutOf {
                "generated" / "*" { anyFile() }
                "build" / "*" { ignore() }
            }

            named.shape() shouldBe plain.shape()
        }

        "モジュールの中に書いた capture も \"*\" と同じ shape になる（プロジェクトを見ていないインデックス）" {
            layoutOf {
                ":feature:*".module { mainSourceSet / kotlin / capture("pkg") / "*.kt".file() }
            }.shape() shouldBe layoutOf {
                ":feature:*".module { mainSourceSet / kotlin / "*" / "*.kt".file() }
            }.shape()
        }

        "実プロジェクトの index で展開しても \"*\" と同じ shape になる" {
            val index = moduleIndexOf("feature/home", "feature/settings")

            layoutOf(index) {
                ":feature:*".module { mainSourceSet / kotlin / capture("pkg") / "*ViewModel".ktFile() }
            }.shape() shouldBe layoutOf(index) {
                ":feature:*".module { mainSourceSet / kotlin / "*" / "*ViewModel".ktFile() }
            }.shape()
        }

        "required と description も * と同じ" {
            val named = layoutOf {
                capture("x") {
                    description = "説明"
                    "build.gradle.kts".file()
                }
            }

            named.shape() shouldBe listOf(
                "* [Directory] \"説明\"",
                // パスにワイルドカードが残るので required にならない。* と同じ。
                "*/build.gradle.kts [File]",
            )
        }
    }

    "名前の規則" - {
        listOf("1feature", "feat ure", "", "a/b").forEach { name ->
            "識別子の規則に合わない名前 \"$name\" は KatachiInvalidIdentifierException" {
                val failure = shouldThrow<KatachiInvalidIdentifierException> {
                    layoutOf { "feature" / capture(name) / "a.kt".file() }
                }

                failure.kind shouldBe DeclarationKind.Capture
                failure.name shouldBe name
                failure.message!! shouldContain "capture"
                failure.message!! shouldContain "--arg"
            }
        }

        "ハイフンとアンダースコアを含む名前は通る" {
            layoutOf { capture("feature-name_2") / "a.kt".file() }.at("*").captureVariants shouldBe
                listOf(LayoutCaptures(listOf(PathCapture(0, "feature-name_2")), null))
        }
    }

    "同じパスで同じ名前" - {
        "1つのパスで同じ名前を2回使うと KatachiDuplicateCaptureException" {
            val failure = shouldThrow<KatachiDuplicateCaptureException> {
                layoutOf { capture("x") / "a" / capture("x") / "a.kt".file() }
            }

            failure.name shouldBe "x"
            failure.role shouldBe "group.Role"
            failure.path shouldBe "*/a/*"
            failure.message!! shouldContain "--arg x="
        }

        "同じセグメントの中で同じ名前を2回使っても KatachiDuplicateCaptureException" {
            // レビューで見つかったバグの再現: CaptureTrail.enter は上の階層から集めた declaredAt
            // としか照らしていなかったので、newNames 自身の中の重複（同じセグメントの部分一致）は
            // 素通りしていた。
            val failure = shouldThrow<KatachiDuplicateCaptureException> {
                layoutOf { "${capture("a")}-${capture("a")}.kt".file() }
            }

            failure.name shouldBe "a"
        }

        "ブロックをまたいだ入れ子でも同じパスなら KatachiDuplicateCaptureException" {
            shouldThrow<KatachiDuplicateCaptureException> {
                layoutOf { capture("x") { capture("x") / "a.kt".file() } }
            }.name shouldBe "x"
        }

        "モジュールの capture とパスの capture が同名でも KatachiDuplicateCaptureException" {
            shouldThrow<KatachiDuplicateCaptureException> {
                layoutOf { ":feature:${capture("feature")}".module { capture("feature") / "a.kt".file() } }
            }.name shouldBe "feature"
        }

        "同じ役割の別パスで同じ名前を使うのは通る" {
            val entries = layoutOf {
                "feature" / capture("feature") / "a.kt".file()
                "test" / capture("feature") / "b.kt".file()
            }

            entries.at("feature/*/a.kt").captureVariants.single().names shouldBe listOf("feature")
            entries.at("test/*/b.kt").captureVariants.single().names shouldBe listOf("feature")
        }

        "別の layout ブロックで同じ名前を使うのは通る" {
            val entries = architecture {
                "group".group {
                    "Role" {
                        layout { "feature" / capture("feature") / "a.kt".file() }
                        layout { "test" / capture("feature") / "b.kt".file() }
                    }
                }
            }.flattenLayout()

            entries.map { it.path } shouldContainExactlyInAnyOrder listOf(
                "feature",
                "feature/*",
                "feature/*/a.kt",
                "test",
                "test/*",
                "test/*/b.kt",
            )
        }

        "同じ名前の兄弟ディレクトリは別パスなので通る" {
            val entries = layoutOf {
                capture("x") / "a.kt".file()
                capture("x") / "b.kt".file()
            }

            entries.at("*/a.kt").captureVariants.single().names shouldBe listOf("x")
            entries.at("*/b.kt").captureVariants.single().names shouldBe listOf("x")
        }
    }

    "隣り合う capture" - {
        "capture 同士が間に文字を挟まず並ぶと KatachiAdjacentCaptureException" {
            val failure = shouldThrow<KatachiAdjacentCaptureException> {
                layoutOf { "${capture("a")}${capture("b")}".file() }
            }

            failure.names shouldBe listOf("a", "b")
        }

        "capture の直前が名前の無い * だと KatachiAdjacentCaptureException" {
            val failure = shouldThrow<KatachiAdjacentCaptureException> {
                layoutOf { "*${capture("a")}".file() }
            }

            failure.names shouldBe listOf("a")
        }

        "capture の直後が名前の無い * だと KatachiAdjacentCaptureException" {
            val failure = shouldThrow<KatachiAdjacentCaptureException> {
                layoutOf { "${capture("a")}*".file() }
            }

            failure.names shouldBe listOf("a")
        }

        "間に文字を1つでも挟めば通る" {
            layoutOf { "${capture("a")}-${capture("b")}".file() }.at("*-*").captureVariants
                .single().names shouldBe listOf("a", "b")
        }
    }

    "layout のキー以外に埋め込んだ capture" - {
        "description に埋め込むと KatachiStrayCaptureTokenException" {
            val failure = shouldThrow<KatachiStrayCaptureTokenException> {
                layoutOf { "dir" { description = capture("x") } }
            }

            failure.name shouldBe "x"
            failure.where shouldBe "a description"
        }

        "fileConstraint の名前に埋め込んでも KatachiStrayCaptureTokenException" {
            val failure = shouldThrow<KatachiStrayCaptureTokenException> {
                layoutOf { fileConstraint(capture("x"), check = silent()) }
            }

            failure.name shouldBe "x"
        }

        "テンプレートの戻り値に紛れ込んでも KatachiStrayCaptureTokenException" {
            // capture(...) のトークンは layout のキーだけが読み戻す。この token が、たとえば
            // 外側の capture() を明示的な receiver 経由で呼ぶなどしてファイルの中身に紛れ込んでも、
            // layout のキーには戻らないので同じ例外になる。
            val arch = architectureOf {
                "domain".group {
                    "UseCase" {
                        layout { "UseCase.kt".file().template { captureToken("leak") } }
                    }
                }
            }

            val failure = shouldThrow<KatachiStrayCaptureTokenException> { arch.generated("UseCase") }
            failure.name shouldBe "leak"
        }
    }

    "平坦化が capture の位置を運ぶ" - {
        "パスの capture はセグメントの位置と名前で記録される" {
            val entries = layoutOf {
                "feature" / capture("feature") / "src" / capture("layer") / "*ViewModel".ktFile()
            }

            entries.at("feature/*/src/*/*ViewModel.kt").captureVariants shouldBe listOf(
                LayoutCaptures(listOf(PathCapture(1, "feature"), PathCapture(3, "layer")), null),
            )
            entries.at("feature/*").captureVariants shouldBe listOf(
                LayoutCaptures(listOf(PathCapture(1, "feature")), null),
            )
            entries.at("feature").captureVariants shouldBe emptyList()
        }

        "名前の無い * は記録されない" {
            layoutOf { "feature" / "*" / "*ViewModel".ktFile() }
                .forEach { it.captureVariants shouldBe emptyList() }
        }

        "同じパスを名前付きと名前なしで宣言すると variants が2つになり、path は1エントリのまま" {
            val entries = layoutOf {
                "feature" / capture("feature") / "a.kt".file()
                "feature" / "*" / "a.kt".file()
            }

            entries.count { it.path == "feature/*/a.kt" } shouldBe 1
            entries.at("feature/*/a.kt").captureVariants shouldBe listOf(
                LayoutCaptures(listOf(PathCapture(1, "feature")), null),
                LayoutCaptures.NONE,
            )
        }
    }
})

