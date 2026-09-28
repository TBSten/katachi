package me.tbsten.katachi.test.template

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.dsl.gradle.module
import me.tbsten.katachi.dsl.template
import me.tbsten.katachi.template.KatachiAmbiguousTemplateException
import me.tbsten.katachi.template.KatachiUnknownTemplateException
import me.tbsten.katachi.test.check.architectureOf
import me.tbsten.katachi.test.dsl.moduleIndexOf

/**
 * `--arg template=` resolution: how a specifier is read against the table of declared templates
 * -- design draft section 3.
 */
class TemplateSpecifierSpec : FreeSpec({
    "役割名（テンプレートが1つ）で選べる" {
        val arch = architectureOf {
            "domain".group {
                "UseCase" {
                    layout { "useCase" / "UseCase.kt".file().template { "// use case" } }
                }
            }
        }
        arch.generatedPaths("UseCase") shouldContainExactly listOf("useCase/UseCase.kt")
    }

    "役割名.id でも選べる（1つでも）" {
        val arch = architectureOf {
            "domain".group {
                "UseCase" {
                    layout { "useCase" / "UseCase.kt".file().template(id = "useCase") { "// use case" } }
                }
            }
        }
        arch.generatedPaths("UseCase.useCase") shouldContainExactly listOf("useCase/UseCase.kt")
    }

    "group 付きで選べる" {
        val arch = architectureOf {
            "data".group {
                "Repository" {
                    layout { "repository" / "Repository.kt".file().template { "// repo" } }
                }
            }
        }
        arch.generatedPaths("data.Repository") shouldContainExactly listOf("repository/Repository.kt")
    }

    "入れ子の group でも選べる" {
        val arch = architectureOf {
            "a".group {
                "b".group {
                    "Role" {
                        layout { "role" / "Role.kt".file().template { "// role" } }
                    }
                }
            }
        }
        arch.generatedPaths("a.b.Role") shouldContainExactly listOf("role/Role.kt")
    }

    "group を省いても、役割名が全体で1つなら通る" {
        val arch = architectureOf {
            "data".group {
                "Repository" {
                    layout { "repository" / "Repository.kt".file().template { "// repo" } }
                }
            }
        }
        arch.generatedPaths("Repository") shouldContainExactly listOf("repository/Repository.kt")
    }

    "group を省いた指定が2通りに読めたら、両方並べて Ambiguous になる" {
        // "Repository.repositoryImpl" は「group Repository の役割 repositoryImpl（テンプレート1つ）」
        // とも、「役割名 Repository（全体で1つ）の id repositoryImpl」とも読める。どちらも宣言として
        // は正しく、群と役割の同名禁止にも触れない（親が違う）ので、この曖昧さは宣言時には見つからない。
        val arch = architectureOf {
            "Repository".group {
                "repositoryImpl" {
                    layout { "repositoryImpl" / "Content.kt".file().template { "// A" } }
                }
            }
            "data".group {
                "Repository" {
                    layout { "repository" / "Repository.kt".file().template(id = "repositoryImpl") { "// B" } }
                }
            }
        }
        val thrown = shouldThrow<KatachiAmbiguousTemplateException> { arch.generated("Repository.repositoryImpl") }
        thrown.candidates shouldContainExactly listOf("Repository.repositoryImpl", "data.Repository.repositoryImpl")
    }

    "group を省いた役割名が2つ以上の役割に当たると、Unknown ではなく Ambiguous になる" {
        // レビューで見つかった旧実装からの退行の再現: byName.singleOrNull() が 2件以上を null に
        // していたため、"Repository" は「その名前のテンプレートは無い」という誤った Unknown に
        // なっていた（候補が実在するのに）。
        val arch = architectureOf {
            "a".group {
                "Repository" { layout { "a" / "Repository.kt".file().template { "// a" } } }
            }
            "b".group {
                "Repository" { layout { "b" / "Repository.kt".file().template { "// b" } } }
            }
        }

        val thrown = shouldThrow<KatachiAmbiguousTemplateException> { arch.generated("Repository") }
        thrown.candidates shouldContainExactly listOf("a.Repository", "b.Repository")
    }

    "2つ以上の役割を役割名だけで書いたら、role.id を並べて Ambiguous になる" {
        val arch = architectureOf {
            "data".group {
                "Repository" {
                    layout {
                        "repository" / "Repository.kt".file().template(id = "repository") { "" }
                        "repository" / "RepositoryImpl.kt".file().template(id = "repositoryImpl") { "" }
                    }
                }
            }
        }
        val thrown = shouldThrow<KatachiAmbiguousTemplateException> { arch.generated("Repository") }
        thrown.candidates shouldContainExactly listOf("data.Repository.repository", "data.Repository.repositoryImpl")
    }

    "知らない指定は Unknown になり、書ける指定を並べる" {
        val arch = architectureOf {
            "domain".group {
                "UseCase" {
                    layout { "useCase" / "UseCase.kt".file().template { "// use case" } }
                }
            }
        }
        val thrown = shouldThrow<KatachiUnknownTemplateException> { arch.generated("UseCse") }
        thrown.declaredTemplates shouldContainExactly listOf("domain.UseCase")
    }

    "テンプレートの無い役割を指定すると Unknown になる（テンプレートの一覧に無い）" {
        val arch = architectureOf {
            "domain".group {
                "UseCase" { layout { "useCase" / "*UseCase.kt".file() } }
            }
        }
        val thrown = shouldThrow<KatachiUnknownTemplateException> { arch.generated("UseCase") }
        thrown.declaredTemplates shouldBe emptyList()
    }

    "完全な指定（group.role.id）は2通りに当たらない" {
        val arch = architectureOf {
            "data".group {
                "Repository" {
                    layout {
                        "repository" / "Repository.kt".file().template(id = "repository") { "" }
                        "repository" / "RepositoryImpl.kt".file().template(id = "repositoryImpl") { "" }
                    }
                }
            }
        }
        arch.generatedPaths("data.Repository.repository") shouldContainExactly listOf("repository/Repository.kt")
    }

    "ループで id を変えながら呼んだ .template は、同じソース行でも別のテンプレートとして残る（仕様の節4）" {
        // レビューで見つかったバグの再現: LayoutTemplate.equals が declaredAt だけで比較していると、
        // この forEach の1行から2回呼ばれる .template(id = id) が「同じテンプレート」に潰れ、
        // 2つ目（settings）が一覧からも生成からも消えていた。
        val arch = architectureOf {
            "data".group {
                "Repository" {
                    layout {
                        listOf("user", "settings").forEach { id ->
                            "$id" / "Repository.kt".file().template(id = id) { "// $id" }
                        }
                    }
                }
            }
        }
        arch.generatedPaths("data.Repository.user") shouldContainExactly listOf("user/Repository.kt")
        arch.generatedPaths("data.Repository.settings") shouldContainExactly listOf("settings/Repository.kt")
        arch.generated(listOf("data.Repository.user", "data.Repository.settings")).keys.sorted() shouldContainExactly
            listOf("settings/Repository.kt", "user/Repository.kt")
    }

    "モジュールのワイルドカードの展開は、id が同じなら引き続き1つのテンプレートにまとまる" {
        val arch = architectureOf {
            "feature".group {
                "Screen" {
                    layout {
                        ":feature:${capture("feature")}".module { "Screen.kt".file().template { "// screen" } }
                    }
                }
            }
        }
        val features = { moduleIndexOf("feature/home", "feature/settings") }
        arch.generated("feature.Screen", mapOf("feature" to "home"), features).keys.sorted() shouldContainExactly
            listOf("feature/home/Screen.kt")
    }
})
