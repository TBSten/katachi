package me.tbsten.katachi.test.check

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.core.spec.style.FreeSpec
import me.tbsten.katachi.check.KatachiArchitectureAssertionError
import me.tbsten.katachi.check.assert
import me.tbsten.katachi.dsl.architecture

class AssertSpec : FreeSpec({
    "違反が無ければ何も投げずに返る" {
        shouldNotThrowAny {
            layoutArchitecture { ".gitignore".file() }
                .assert(repositoryOf { ".gitignore"() })
        }
    }

    "違反があれば AssertionError のサブタイプを投げる" {
        val failure = shouldThrow<KatachiArchitectureAssertionError> {
            layoutArchitecture { ".gitignore".file() }
                .assert(repositoryOf { ".gitignore"(); "notes.md"() })
        }

        // JUnit も kotest も AssertionError をテストの失敗として扱う。katachi 側は
        // どのテストフレームワークにも依存しない。
        failure.shouldBeInstanceOf<AssertionError>()
        failure.message!! shouldStartWith "Katachi check failed: 1 violation (Unexpected: 1)"
        failure.message!! shouldContain "[UnexpectedFile] notes.md"
    }

    "引数なしの assert は実ファイルシステムと実際の作業ディレクトリを見る" {
        // The working directory is katachi/, so the root has to be found by walking up, and
        // the tree that comes back has to be this repository's own.
        val repository = architecture {
            // No `files` here: the point is that the default reaches the real tree.
            "app".group { "Role" { layout { "settings.gradle.kts".file() } } }
        }
        val failure = shouldThrow<KatachiArchitectureAssertionError> { repository.assert() }
        val paths = failure.violations.map { it.path }

        paths shouldContain "README.md"
        paths shouldContain "katachi"
        paths shouldNotContain "settings.gradle.kts"
        // `files` is left at its default, so git decides what belongs to the project. These
        // exist on a developer's machine and are absent on CI; neither may be reported.
        paths shouldNotContain ".local"
        paths shouldNotContain "local.properties"
    }

    "例外は打ち切りに関係なく全件の違反を持つ" {
        val failure = shouldThrow<KatachiArchitectureAssertionError> {
            layoutArchitecture { ".gitignore".file() }
                .assert(
                    repositoryOf {
                        ".gitignore"()
                        repeat(12) { index -> "note-$index.md"() }
                    },
                    maxViolations = 2,
                )
        }

        failure.violations.size shouldBe 12
        failure.message!!.lines().count { it.startsWith("[") } shouldBe 2
        failure.message!!.lines().last() shouldBe "Showing first 2 (10 more)"
    }
})
