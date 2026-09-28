package me.tbsten.katachi.test.template

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.dsl.template
import me.tbsten.katachi.processor.process
import me.tbsten.katachi.template.DescribeTemplates
import me.tbsten.katachi.template.KatachiAmbiguousTemplateException
import me.tbsten.katachi.template.KatachiMultipleTemplatesToDescribeException
import me.tbsten.katachi.template.KatachiUnknownTemplateException
import me.tbsten.katachi.template.TemplateDetail
import me.tbsten.katachi.template.TemplateList
import me.tbsten.katachi.test.check.architectureOf

class DescribeTemplatesSpec : FreeSpec({
    "一覧" - {
        "宣言順にテンプレートを並べる" {
            val arch = architectureOf {
                "a".group { "A" { layout { "a" / "A.kt".file().template { "" } } } }
                "b".group { "B" { layout { "b" / "B.kt".file().template { "" } } } }
            }
            val list = arch.process(DescribeTemplates, DescribeTemplates.Args()).getOrThrow() as TemplateList
            list.templates.map { it.template } shouldContainExactly listOf("a.A", "b.B")
        }

        "title は .template(title=) → id → 役割の title の順" {
            val arch = architectureOf {
                "domain".group {
                    "UseCase" {
                        title = "Use case"
                        layout {
                            "a" / "A.kt".file().template(id = "withTitle", title = "With title") { "" }
                            "b" / "B.kt".file().template(id = "withoutTitle") { "" }
                        }
                    }
                }
            }
            val list = arch.process(DescribeTemplates, DescribeTemplates.Args()).getOrThrow() as TemplateList
            list.templates.single { it.id == "withTitle" }.title shouldBe "With title"
            list.templates.single { it.id == "withoutTitle" }.title shouldBe "withoutTitle"
        }

        "id が無く役割にも title が無ければ、役割の qualifiedName にまで落ちる" {
            val arch = architectureOf {
                "domain".group { "UseCase" { layout { "a" / "A.kt".file().template { "" } } } }
            }
            val list = arch.process(DescribeTemplates, DescribeTemplates.Args()).getOrThrow() as TemplateList
            list.templates.single().title shouldBe "domain.UseCase"
        }

        "role の summary をそのまま運ぶ" {
            val arch = architectureOf {
                "domain".group {
                    "UseCase" {
                        summary = "A single app-specific behavior"
                        layout { "a" / "A.kt".file().template { "" } }
                    }
                }
            }
            val list = arch.process(DescribeTemplates, DescribeTemplates.Args()).getOrThrow() as TemplateList
            list.templates.single().summary shouldBe "A single app-specific behavior"
        }

        "capture と parameter が衝突するテンプレートは conflict=true で残り、一覧全体は失敗しない" {
            val arch = architectureOf {
                "a".group {
                    "A" {
                        layout {
                            "a" / "${capture("name")}A.kt".file().template {
                                val name by stringParameter()
                                name
                            }
                        }
                    }
                }
                "b".group { "B" { layout { "b" / "B.kt".file().template { "// ok" } } } }
            }
            val list = arch.process(DescribeTemplates, DescribeTemplates.Args()).getOrThrow() as TemplateList
            list.templates.single { it.template == "a.A" }.conflict shouldBe true
            list.templates.single { it.template == "b.B" }.conflict shouldBe false
        }
    }

    "詳細" - {
        "--arg template= で1つのテンプレートを選べる" {
            val arch = architectureOf {
                "domain".group { "UseCase" { layout { "a" / "A.kt".file().template { "class A" } } } }
            }
            val detail = arch.process(DescribeTemplates, DescribeTemplates.Args(template = "domain.UseCase"))
                .getOrThrow() as TemplateDetail
            detail.roleName shouldBe "domain.UseCase"
            detail.files.single().content shouldBe "class A"
        }

        "知らない指定は KatachiUnknownTemplateException" {
            val arch = architectureOf {
                "domain".group { "UseCase" { layout { "a" / "A.kt".file().template { "" } } } }
            }
            shouldThrow<KatachiUnknownTemplateException> {
                arch.process(DescribeTemplates, DescribeTemplates.Args(template = "Nope")).getOrThrow()
            }
        }

        "曖昧な指定は KatachiAmbiguousTemplateException" {
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
            shouldThrow<KatachiAmbiguousTemplateException> {
                arch.process(DescribeTemplates, DescribeTemplates.Args(template = "Repository")).getOrThrow()
            }
        }

        ", を含む指定は KatachiMultipleTemplatesToDescribeException" {
            val arch = architectureOf {
                "a".group { "A" { layout { "a" / "A.kt".file().template { "" } } } }
                "b".group { "B" { layout { "b" / "B.kt".file().template { "" } } } }
            }
            val thrown = shouldThrow<KatachiMultipleTemplatesToDescribeException> {
                arch.process(DescribeTemplates, DescribeTemplates.Args(template = "a.A,b.B")).getOrThrow()
            }
            thrown.template shouldBe "a.A,b.B"
        }
    }
})
