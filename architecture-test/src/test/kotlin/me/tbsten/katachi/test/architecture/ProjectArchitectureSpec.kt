package me.tbsten.katachi.test.architecture

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.check.FileConstraintCheck
import me.tbsten.katachi.check.assertNoErrors

@OptIn(ExperimentalKatachiApi::class)
class ProjectArchitectureSpec : FreeSpec({
    "リポジトリ全体が projectArchitecture の宣言どおりで、警告も1件も出ない" {
        // `assertNoErrors` fails on a `Severity.Error` exactly as `assert()` does, and hands back
        // the rest of the run -- the warnings. A warning alone is printed to stderr and the test
        // still passes, and a passing test's stderr never reaches the Gradle console, so a
        // `[MissingDescription]` or `[AmbiguousLayout]` introduced by an edit to a `layout { }`
        // would go unnoticed without the `shouldBe`. One call is one walk: `assert()` followed by
        // `validate()` walked the repository and ran every konsist constraint twice.
        projectArchitecture.assertNoErrors(FileConstraintCheck()) shouldBe emptyList()
    }
})
