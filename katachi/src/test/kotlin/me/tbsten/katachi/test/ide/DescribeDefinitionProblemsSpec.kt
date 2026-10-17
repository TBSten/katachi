package me.tbsten.katachi.test.ide

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.dsl.template
import me.tbsten.katachi.ide.internal.DefinitionProblemKind
import me.tbsten.katachi.test.check.architectureOf
import me.tbsten.katachi.test.template.templateSetArchitecture

/**
 * `problems[]` of the definition JSON says where a template, a template set or a constraint that
 * failed was written, and what threw (K-8 of the IDE definition authoring plan).
 */
class DescribeDefinitionProblemsSpec : FreeSpec({
    "TemplatePreviewFailed" - {
        "captureValue の名前の typo は、その行と例外の型を運ぶ" {
            var line = 0
            val definition = architectureOf {
                "feature".group {
                    "Screen".role {
                        layout {
                            "feature" / "${capture("name")}Screen.kt".file().template { line = Throwable().stackTrace.first().lineNumber; captureValue("nmae") }
                        }
                    }
                }
            }.definitionOf(basicProject())

            val problem = definition.problems.single { it.kind == DefinitionProblemKind.TemplatePreviewFailed }
            problem.detail shouldBe "feature.Screen"
            problem.exception.shouldNotBeNull()
            problem.declaredAt.shouldNotBeNull().fileName shouldBe "DescribeDefinitionProblemsSpec.kt"
            problem.declaredAt.shouldNotBeNull().line shouldBe line
        }

        "例外が宣言元を持たなければ、標準ライブラリのフレームを飛ばして利用者の lambda の行になる" {
            val definition = architectureOf {
                "feature".group {
                    "Boom".role { layout { "feature" / "Boom.kt".file().template { error("boom") } } }
                }
            }.definitionOf(basicProject())

            val problem = definition.problems.single { it.kind == DefinitionProblemKind.TemplatePreviewFailed }
            problem.exception shouldBe "java.lang.IllegalStateException"
            problem.declaredAt.shouldNotBeNull().fileName shouldBe "DescribeDefinitionProblemsSpec.kt"
        }
    }

    "TemplateSetPreviewFailed" - {
        "組のプレビューが落ちると、メンバの template(\"…\") の行と例外の型が付く" {
            var line = 0
            val definition = templateSetArchitecture {
                line = Throwable().stackTrace.first().lineNumber + 1
                template("feature.FeatureScreen") { arg("typo", "x") }
            }.definitionOf(basicProject())

            val problem = definition.problems.single { it.kind == DefinitionProblemKind.TemplateSetPreviewFailed }
            problem.exception shouldBe "me.tbsten.katachi.template.KatachiInvalidTemplateSetArgException"
            problem.declaredAt.shouldNotBeNull().line shouldBe line
        }
    }

    "ExplanationFailed" - {
        "説明に失敗した制約は、投げた行と例外の型を運ぶ" {
            val definition = explainedDefinition().definitionOf(explainedProject())

            val problem = definition.problems.single { it.kind == DefinitionProblemKind.ExplanationFailed }
            problem.exception.shouldNotBeNull()
            problem.declaredAt.shouldNotBeNull().fileName shouldBe "DescribeDefinitionSpecSupport.kt"
        }
    }

    "ほかの問題" - {
        "WalkFailed などは declaredAt も exception も持たない" {
            val definition = basicDefinition().definitionOf(rootlessProject())

            val problem = definition.problems.single { it.kind == DefinitionProblemKind.WalkFailed }
            problem.declaredAt.shouldBeNull()
            problem.exception.shouldBeNull()
        }
    }
})
