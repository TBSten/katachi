package me.tbsten.katachi.test.check

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.check.FileConstraintCheck
import me.tbsten.katachi.check.validate
import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.FileConstraint
import me.tbsten.katachi.dsl.FileConstraintAllowedList
import me.tbsten.katachi.dsl.FileConstraintDocumentContext
import me.tbsten.katachi.dsl.FileConstraintFailure
import me.tbsten.katachi.dsl.FileConstraintFix
import me.tbsten.katachi.dsl.FileConstraintReason

/**
 * The optional details a constraint can attach to a failure -- reasons, what would be allowed,
 * how to fix it, and which line of the rule said no -- as values: their defaults, and how they
 * travel from a [FileConstraintFailure] to the `UnsatisfiedFileConstraint` the report prints.
 *
 * What they look like printed is [FileConstraintDetailsReportSpec]'s.
 *
 * NOTE: このファイルのパッケージを `me.tbsten.katachi.check` にしてはいけない。
 * captureDeclarationSite() がライブラリ自身のフレームとして読み飛ばしてしまう。
 */

/** The site a constraint is declared at here, so that it can be compared with a rule's own site. */
private val CONSTRAINT_SITE: DeclarationSite = DeclarationSite("ProjectArchitecture.kt", 61)

/** A failure that says everything there is to say. */
private fun detailedFailure(file: String): FileConstraintFailure = FileConstraintFailure(
    file = file,
    declaration = "GetUserUseCaseImpl",
    line = 9,
    reasons = listOf(FileConstraintReason("Not allowed", "class GetUserUseCaseImpl")),
    allowed = FileConstraintAllowedList("Allowed here", listOf("interface GetUserUseCase")),
    howToFix = listOf(FileConstraintFix("Rename it", code = listOf("allow(`interface`, \"GetUserUseCase\")"))),
    ruleDeclaredAt = DeclarationSite("UseCaseRole.kt", 16),
)

/** Answers with [failures] for every file it is handed, whatever the file. */
private fun answering(vararg failures: (String) -> FileConstraintFailure): FileConstraint =
    FileConstraint { subject -> subject.files.flatMap { file -> failures.map { it(file) } } }

/** A context for asking a constraint for its section directly, outside of page generation. */
private fun documentContextForTest(): FileConstraintDocumentContext = FileConstraintDocumentContext(
    role = architectureOf { "domain".group { "UseCase".role { } } }.allRoles.single(),
    name = null,
    declaredAt = CONSTRAINT_SITE,
    captureNames = emptySet(),
    exampleFile = { throw IllegalStateException("the example file was not supposed to be asked for") },
)

class FileConstraintFailureDetailsSpec : FreeSpec({
    "FileConstraintFailure の新しい欄" - {
        "今までの呼び方がそのまま通り、新しい欄は空" {
            val failure = FileConstraintFailure("alpha/Helper.kt", "Helper", 12)

            failure.reasons.shouldBeEmpty()
            failure.allowed.shouldBeNull()
            failure.howToFix.shouldBeEmpty()
            failure.ruleDeclaredAt.shouldBeNull()
        }

        "直し方のコードは既定で空" {
            FileConstraintFix("Rename it").code.shouldBeEmpty()
        }

        "欄が同じ失敗は等しく、理由が違えば等しくない" {
            detailedFailure("a.kt") shouldBe detailedFailure("a.kt")
            (detailedFailure("a.kt") == detailedFailure("a.kt").copy(reasons = emptyList())) shouldBe false
        }

        "FileConstraint のラムダは documentSection を書かなくてよく、既定は null" {
            val constraint = FileConstraint { emptyList() }

            constraint.documentSection(documentContextForTest()).shouldBeNull()
        }
    }

    "UnsatisfiedFileConstraint に渡る" - {
        "理由・許可の一覧・直し方・規則の行がそのまま渡る" {
            val arch = architectureOf {
                "domain".group {
                    "UseCase".role {
                        fileConstraint(declaredAt = CONSTRAINT_SITE, check = answering(::detailedFailure))
                        layout { "alpha" / "*.kt".file() }
                    }
                }
            }

            val violation = arch.validate(repositoryOf { "alpha" { "A.kt"() } }, FileConstraintCheck()).unsatisfied().single()

            violation.reasons shouldBe listOf(FileConstraintReason("Not allowed", "class GetUserUseCaseImpl"))
            violation.allowed shouldBe FileConstraintAllowedList("Allowed here", listOf("interface GetUserUseCase"))
            violation.howToFix shouldBe listOf(FileConstraintFix("Rename it", code = listOf("allow(`interface`, \"GetUserUseCase\")")))
            violation.ruleDeclaredAt shouldBe DeclarationSite("UseCaseRole.kt", 16)
            violation.declaredAt shouldBe CONSTRAINT_SITE
        }

        "新しい欄を使わない失敗では、どれも空のまま" {
            val arch = architectureOf {
                "domain".group {
                    "UseCase".role {
                        fileConstraint(declaredAt = CONSTRAINT_SITE, check = rejectsEverything())
                        layout { "alpha" / "*.kt".file() }
                    }
                }
            }

            val violation = arch.validate(repositoryOf { "alpha" { "A.kt"() } }, FileConstraintCheck()).unsatisfied().single()

            violation.reasons.shouldBeEmpty()
            violation.allowed.shouldBeNull()
            violation.howToFix.shouldBeEmpty()
            violation.ruleDeclaredAt.shouldBeNull()
        }

        "同じ失敗を2回返したら1件" {
            val arch = architectureOf {
                "domain".group {
                    "UseCase".role {
                        fileConstraint(check = answering(::detailedFailure, ::detailedFailure))
                        layout { "alpha" / "*.kt".file() }
                    }
                }
            }

            arch.validate(repositoryOf { "alpha" { "A.kt"() } }, FileConstraintCheck()).unsatisfied().size shouldBe 1
        }

        "同じ宣言でも理由が違えば2件" {
            val arch = architectureOf {
                "domain".group {
                    "UseCase".role {
                        fileConstraint(
                            check = answering(
                                ::detailedFailure,
                                { file -> detailedFailure(file).copy(reasons = listOf(FileConstraintReason("Reason", "other"))) },
                            ),
                        )
                        layout { "alpha" / "*.kt".file() }
                    }
                }
            }

            arch.validate(repositoryOf { "alpha" { "A.kt"() } }, FileConstraintCheck()).unsatisfied().size shouldBe 2
        }
    }
})
