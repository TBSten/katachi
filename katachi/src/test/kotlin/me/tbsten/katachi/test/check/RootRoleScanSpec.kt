package me.tbsten.katachi.test.check

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.check.validate

/**
 * A role declared straight into `architecture { }` has to reach the walk, not only the model.
 *
 * Each pair below covers the same file twice: once with the root role there, once without it.
 * The second half is what makes the first half mean anything — a definition that allowed
 * everything would pass the first assertion just as well.
 */
class RootRoleScanSpec : FreeSpec({
    "ルート直下の役割だけが覆うファイルは Unexpected にならない" {
        architectureOf {
            "Gitignore" { layout { ".gitignore".file() } }
        }
            .validate(repositoryOf { ".gitignore"() })
            .labels() shouldBe emptyList()
    }

    "その役割を外すと、同じファイルが Unexpected になる" {
        architectureOf { }
            .validate(repositoryOf { ".gitignore"() })
            .labels() shouldBe listOf("[UnexpectedFile] .gitignore")
    }

    "ルート直下の役割が覆うディレクトリの中まで検査される" {
        architectureOf {
            "Documentation" { layout { "docs" / "README.md".file() } }
        }
            .validate(
                repositoryOf {
                    "docs" {
                        "README.md"()
                        "notes.md"()
                    }
                },
            )
            .labels() shouldBe listOf("[UnexpectedFile] docs/notes.md")
    }

    "ルート直下の役割と group 内の役割は同時に効く" {
        architectureOf {
            "Gitignore" { layout { ".gitignore".file() } }
            "domain".group {
                "UseCase" { layout { "useCase" / "GetUserUseCase.kt".file() } }
            }
        }
            .validate(
                repositoryOf {
                    ".gitignore"()
                    "useCase" { "GetUserUseCase.kt"() }
                },
            )
            .labels() shouldBe emptyList()
    }

    "ルート直下の役割が宣言した必須ファイルが無ければ Missing になる" {
        architectureOf {
            "Gitignore" { layout { ".gitignore".file() } }
        }
            .validate(repositoryOf { })
            .labels() shouldBe listOf("[MissingFile] .gitignore")
    }
})
