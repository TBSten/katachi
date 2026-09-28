package me.tbsten.katachi.test.template

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.dsl.template
import me.tbsten.katachi.processor.process
import me.tbsten.katachi.template.DescribeTemplates
import me.tbsten.katachi.template.TemplateDetail
import me.tbsten.katachi.test.check.architectureOf

/**
 * [TemplateDetail.branches]: what another value of a Boolean or enum parameter changes -- the
 * content, or which parameters are declared. A template now always describes the one file its
 * declaration names, so [me.tbsten.katachi.template.TemplateBranch.addedFiles] /
 * [me.tbsten.katachi.template.TemplateBranch.removedFiles] stay empty; what a branch can still add
 * or drop is a parameter written inside the `if`.
 */
class TemplateBranchParametersSpec : FreeSpec({
    fun detailOf(arch: me.tbsten.katachi.dsl.Architecture, template: String): TemplateDetail =
        arch.process(DescribeTemplates, DescribeTemplates.Args(template = template)).getOrThrow() as TemplateDetail

    "if (withImpl) の中のパラメータは、withImpl=true の分岐で addedParameters に出る" {
        val arch = architectureOf {
            "domain".group {
                "UseCase" {
                    layout {
                        "useCase" / "UseCase.kt".file().template {
                            val withImpl by booleanParameter(default = false)
                            if (withImpl) {
                                val implBody by stringParameter(default = "TODO()")
                                "interface UseCase\nclass UseCaseImpl { $implBody }"
                            } else {
                                "interface UseCase"
                            }
                        }
                    }
                }
            }
        }

        val detail = detailOf(arch, "UseCase")
        val branch = detail.branches.single { it.parameterName == "withImpl" && it.value == "true" }
        branch.addedParameters.map { it.name } shouldContainExactly listOf("implBody")
        branch.removedParameters.shouldBeEmpty()
        // 1テンプレート = 1ファイルなので、値が変わってもファイルの増減は無い。
        branch.addedFiles.shouldBeEmpty()
        branch.removedFiles.shouldBeEmpty()
    }

    "分岐しないパラメータは branches に出ない" {
        val arch = architectureOf {
            "domain".group {
                "UseCase" {
                    layout { "useCase" / "UseCase.kt".file().template { val name by stringParameter(); "class $name" } }
                }
            }
        }
        detailOf(arch, "UseCase").branches.shouldBeEmpty()
    }

    "enum の他の値でも中身が変わればブランチに出る" {
        val arch = architectureOf {
            "domain".group {
                "UseCase" {
                    layout {
                        "useCase" / "UseCase.kt".file().template {
                            val visibility by enumParameter(BranchVisibility.Public)
                            "${visibility.name.lowercase()} class UseCase"
                        }
                    }
                }
            }
        }
        val detail = detailOf(arch, "UseCase")
        detail.branches.single().let {
            it.parameterName shouldBe "visibility"
            it.value shouldBe "Internal"
        }
    }
})

private enum class BranchVisibility { Public, Internal }
