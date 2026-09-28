package me.tbsten.katachi.test.dsl

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import me.tbsten.katachi.dsl.DeclarationKind
import me.tbsten.katachi.dsl.KatachiDuplicateTemplateException
import me.tbsten.katachi.dsl.KatachiDuplicateTemplateIdException
import me.tbsten.katachi.dsl.KatachiInvalidIdentifierException
import me.tbsten.katachi.dsl.KatachiMissingTemplateIdException
import me.tbsten.katachi.dsl.KatachiTemplateOnWildcardException
import me.tbsten.katachi.dsl.LayoutEntryKind
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.gradle.module
import me.tbsten.katachi.dsl.internal.Template
import me.tbsten.katachi.dsl.internal.flattenLayout
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.dsl.template

/**
 * `.template { }`, the layout-metadata sugar `LayoutFile.template` attaches. Value binding,
 * defaults and the checks every parameter type shares are [TemplateTypedParameterCheckSpec]'s;
 * this covers what is specific to attaching a template to a file declaration -- where it can be
 * attached, that it changes nothing about the flattened layout or its violations, and the
 * declaration-time checks around `id`.
 */
class LayoutTemplateSpec : FreeSpec({
    "付けられる対象" - {
        "file() に付けられる" {
            layoutOf { "A".file().template { "" } }.single { it.path == "A" }[Template]
                .shouldNotBeNull()
        }

        "ktFile() に付けられる" {
            layoutOf { "A".ktFile().template { "" } }.single { it.path == "A.kt" }[Template]
                .shouldNotBeNull()
        }

        ".optional() の前でも後でも付けられる" {
            layoutOf { "A".file().optional().template { "" } }.single { it.path == "A" }[Template]
                .shouldNotBeNull()
            layoutOf { "A".file().template { "" }.optional() }.single { it.path == "A" }[Template]
                .shouldNotBeNull()
        }

        ".metadata { } の前でも後でも付けられる" {
            layoutOf { "A".file().metadata { }.template { "" } }.single { it.path == "A" }[Template]
                .shouldNotBeNull()
            layoutOf { "A".file().template { "" }.metadata { } }.single { it.path == "A" }[Template]
                .shouldNotBeNull()
        }

        "/ の末尾のファイルに付く。途中の値には付け替えの影響が及ばない" {
            val entries = layoutOf { "dir" / "A".file().template { "" } }
            entries.single { it.path == "dir/A" }[Template].shouldNotBeNull()
            entries.single { it.path == "dir" }[Template].shouldBeNull()
        }
    }

    "平坦化の結果と違反は変わらない" - {
        ".template を付けた定義と外した定義で path・kind・required・description が同じ" {
            val withTemplate = layoutOf { "useCase" / "${capture("name")}UseCase".ktFile().template { "" } }
            val plain = layoutOf { "useCase" / "${capture("name")}UseCase".ktFile() }

            withTemplate.shape() shouldBe plain.shape()
        }
    }

    "ブロックは平坦化で走らない" - {
        "宣言してもブロックは呼ばれない" {
            var calls = 0
            layoutOf { "A".file().template { calls++; "" } }

            calls shouldBe 0
        }
    }

    "2回付け" - {
        "同じ宣言に2回 .template を書くと KatachiDuplicateTemplateException" {
            val thrown = shouldThrow<KatachiDuplicateTemplateException> {
                layoutOf { "A".file().template { "a" }.template { "b" } }
            }

            thrown.path shouldBe "A"
            thrown.role shouldBe "group.Role"
        }

        "別の宣言が平坦化で同じパスに合流し、両方に template があっても KatachiDuplicateTemplateException" {
            val thrown = shouldThrow<KatachiDuplicateTemplateException> {
                layoutOf {
                    "dir" / "A".file().template { "a" }
                    "dir" / "A".file().template { "b" }
                }
            }

            thrown.path shouldBe "dir/A"
        }

        "片方にしか付けなければ合流しても通る" {
            val entries = layoutOf {
                "dir" / "A".file().template { "a" }
                "dir" / "A".file()
            }

            entries.count { it.path == "dir/A" } shouldBe 1
            entries.single { it.path == "dir/A" }[Template].shouldNotBeNull()
        }
    }

    "id" - {
        "1つしか無ければ省略できる" {
            val entries = layoutOf { "A".file().template { "" } }
            entries.single { it.path == "A" }[Template]?.id.shouldBeNull()
        }

        "識別子でない id は KatachiInvalidIdentifierException" {
            val thrown = shouldThrow<KatachiInvalidIdentifierException> {
                layoutOf { "A".file().template(id = "not valid") { "" } }
            }

            thrown.kind shouldBe DeclarationKind.TemplateId
        }

        "役割の中で2つ以上あるのに片方が省略していると KatachiMissingTemplateIdException" {
            val thrown = shouldThrow<KatachiMissingTemplateIdException> {
                layoutOf {
                    "A".file().template { "a" }
                    "B".file().template(id = "b") { "b" }
                }
            }

            thrown.otherIds shouldBe listOf("b")
        }

        "同じ役割で id が重複すると KatachiDuplicateTemplateIdException" {
            val thrown = shouldThrow<KatachiDuplicateTemplateIdException> {
                layoutOf {
                    "A".file().template(id = "x") { "a" }
                    "B".file().template(id = "x") { "b" }
                }
            }

            thrown.id shouldBe "x"
        }

        "モジュールのワイルドカードの展開で並ぶ、同じ LayoutTemplate インスタンスの id 重複は許される" {
            val index = moduleIndexOf("feature/home", "feature/settings")

            val entries = shouldNotThrowAny {
                layoutOf(index) {
                    ":feature:*".module { "Screen".file().template(id = "screen") { "" } }
                }
            }

            // home と settings の2モジュールに展開されるが、同じ .template { } 呼び出し
            // （＝同じ LayoutTemplate インスタンス）が両方に載っているだけなので通る。
            val templates = entries.mapNotNull { it[Template] }
            templates.size shouldBe 2
            templates.distinct().size shouldBe 1
        }

        "ループで作った宣言は id をループ変数から作れる（仕様の節4）" {
            val entries = layoutOf {
                listOf("User", "Post").forEach { name ->
                    val id = name.replaceFirstChar(Char::lowercaseChar)
                    "${name}Repository".file().template(id = id) { "" }
                    "${name}RepositoryImpl".file().template(id = "${id}Impl") { "" }
                }
            }

            entries.mapNotNull { it[Template]?.id } shouldContainExactlyInAnyOrder
                listOf("user", "userImpl", "post", "postImpl")
        }
    }

    "名前の無いワイルドカード" - {
        "名前の無い * を含むパスへの .template は KatachiTemplateOnWildcardException" {
            val thrown = shouldThrow<KatachiTemplateOnWildcardException> {
                layoutOf { "dir" / "*Repository".ktFile().template { "" } }
            }

            thrown.path shouldContain "*Repository.kt"
        }

        "capture() で名前を付ければ通る" {
            val entries = layoutOf { "dir" / "${capture("name")}Repository".ktFile().template { "" } }
            entries.single { it.kind == LayoutEntryKind.File }[Template].shouldNotBeNull()
        }

        "名前の無いモジュールの * への .template も KatachiTemplateOnWildcardException" {
            shouldThrow<KatachiTemplateOnWildcardException> {
                layoutOf { ":feature:*".module { "A".file().template { "" } } }
            }
        }

        "モジュールの * に capture で名前を付ければ通る" {
            val entries = layoutOf {
                ":feature:${capture("feature")}".module { "A".file().template { "" } }
            }
            entries.single { it.path == "feature/*/A" }[Template].shouldNotBeNull()
        }

        "エスケープしたリテラルの \\* は名前の無いワイルドカードに数えない" {
            // レビューで見つかったバグの再現: hasOwnUnnamedWildcard は segment 中の '*' を
            // そのまま数えていたので、captureSegment を持たないリテラルの \\* も1つと数え、
            // 名前の無いワイルドカードが残っていると誤って判定していた。
            val entries = layoutOf { "dir" / "Foo\\*.kt".file().template { "" } }
            entries.single { it.path == "dir/Foo\\*.kt" }[Template].shouldNotBeNull()
        }
    }

    "宣言の誤りは平坦化（assert() が最初に読む段）で見つかる" - {
        "flattenLayout() を呼んだだけで例外になる -- assert() はこれと同じ evaluateLayout を先に読む" {
            shouldThrow<KatachiTemplateOnWildcardException> {
                architecture {
                    "group".group {
                        "Role" { layout { "*Repository".ktFile().template { "" } } }
                    }
                }.flattenLayout()
            }
        }
    }
})
