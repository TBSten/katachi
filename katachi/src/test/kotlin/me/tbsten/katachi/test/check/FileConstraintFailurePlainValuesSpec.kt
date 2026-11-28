package me.tbsten.katachi.test.check

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.FileConstraintAllowedList
import me.tbsten.katachi.dsl.FileConstraintFailure
import me.tbsten.katachi.dsl.FileConstraintFix
import me.tbsten.katachi.dsl.FileConstraintReason

/**
 * The way to build a [FileConstraintFailure] from plain values: `label to text` pairs, a heading
 * with its entries, and one sentence per fix.
 *
 * It answers with the same data class the constructor does, so what is pinned is that the two
 * routes meet at an equal value.
 */
class FileConstraintFailurePlainValuesSpec : FreeSpec({
    "値だけで組み立てた FileConstraintFailure" - {
        "コンストラクタで同じ値から作った失敗と等しい" {
            val site = DeclarationSite("UseCaseRole.kt", 16)

            val plain = FileConstraintFailure(
                file = "core/domain/GetUserUseCase.kt",
                declaration = "GetUserUseCaseImpl",
                line = 9,
                reasons = listOf("Not allowed" to "class GetUserUseCaseImpl", "Prohibited by" to "no Impl"),
                allowed = "Allowed here" to listOf("interface GetUserUseCase"),
                howToFix = listOf("Rename it to GetUserUseCase", "Move it"),
                ruleDeclaredAt = site,
            )

            plain shouldBe FileConstraintFailure(
                file = "core/domain/GetUserUseCase.kt",
                declaration = "GetUserUseCaseImpl",
                line = 9,
                reasons = listOf(
                    FileConstraintReason("Not allowed", "class GetUserUseCaseImpl"),
                    FileConstraintReason("Prohibited by", "no Impl"),
                ),
                allowed = FileConstraintAllowedList("Allowed here", listOf("interface GetUserUseCase")),
                howToFix = listOf(FileConstraintFix("Rename it to GetUserUseCase"), FileConstraintFix("Move it")),
                ruleDeclaredAt = site,
            )
        }

        "許可の一覧と直し方を渡さなければ、どちらも空のまま" {
            val plain = FileConstraintFailure(file = "a.kt", reasons = listOf("Missing" to "x"))

            plain.allowed shouldBe null
            plain.howToFix shouldBe emptyList()
            plain.reasons shouldBe listOf(FileConstraintReason("Missing", "x"))
        }

        "直し方の文は、コードを持たない FileConstraintFix になる" {
            val plain = FileConstraintFailure(file = "a.kt", reasons = emptyList(), howToFix = listOf("Do it"))

            plain.howToFix shouldBe listOf(FileConstraintFix("Do it", code = emptyList()))
        }

        "同じ宣言を 2 回返しても 1 件に数えられる（データクラスの等値性）" {
            val failures = List(2) { FileConstraintFailure(file = "a.kt", reasons = listOf("Missing" to "x")) }

            failures.toSet().size shouldBe 1
        }
    }
})
