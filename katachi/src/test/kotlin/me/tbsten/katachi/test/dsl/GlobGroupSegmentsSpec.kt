package me.tbsten.katachi.test.dsl

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.dsl.internal.Glob
import me.tbsten.katachi.dsl.internal.GlobGroupKind

/**
 * Which level each wildcard of a [Glob] sits on, and what it matched there -- the two halves
 * `LayoutEntry.captureValuesOf` pairs a `capture("...")` name with.
 */
class GlobGroupSegmentsSpec : FreeSpec({
    "groupSegments" - {
        "ワイルドカードごとに、それがある区切りの番号を持つ" {
            val glob = Glob.compile("feature/*/src/**/*Screen.kt")

            glob.groupKinds shouldContainExactly listOf(GlobGroupKind.Single, GlobGroupKind.Recursive, GlobGroupKind.Single)
            glob.groupSegments shouldContainExactly listOf(1, 3, 4)
        }

        "1 つの区切りに 2 つの * があれば同じ番号が 2 つ並ぶ" {
            Glob.compile("data/*-*.kt").groupSegments shouldContainExactly listOf(1, 1)
        }

        "先頭の区切りの後から数える" {
            Glob.compile(":feature:*:**", Glob.MODULE_SEPARATOR).groupSegments shouldContainExactly listOf(1, 2)
        }

        "エスケープした * は数えない" {
            Glob.compile("""a/\*/*.kt""").groupSegments shouldContainExactly listOf(2)
        }

        "どのパターンでも groupKinds と同じ数だけ並ぶ" {
            val patterns = listOf("*", "**", "a/**", "**/b", "a/**/b/*", "*/*/*", "x*y/z", "build.gradle.kts", "a/*-*-*.kt")
            patterns.forEach { pattern ->
                val glob = Glob.compile(pattern)
                withClue(pattern) { glob.groupSegments.size shouldBe glob.groupKinds.size }
            }
        }
    }

    "matchGroups" - {
        "ワイルドカードごとに当たった文字列を、groupKinds と同じ並びで返す" {
            Glob.compile("feature/*/src/**/*Screen.kt").matchGroups("feature/home/src/a/b/HomeScreen.kt")
                .shouldNotBeNull() shouldContainExactly listOf("home", "a/b", "Home")
        }

        "何にも当たらなかった ** は空文字列で、位置は保たれる" {
            Glob.compile("feature/*/src/**/*Screen.kt").matchGroups("feature/home/src/HomeScreen.kt")
                .shouldNotBeNull() shouldContainExactly listOf("home", "", "Home")
        }

        "1 つの区切りの 2 つの * をそれぞれ返す" {
            Glob.compile("data/*-*.kt").matchGroups("data/user-remote.kt")
                .shouldNotBeNull() shouldContainExactly listOf("user", "remote")
        }

        "当たらなければ null" {
            Glob.compile("feature/*/src/**/*Screen.kt").matchGroups("core/home/src/HomeScreen.kt").shouldBeNull()
        }

        "ワイルドカードが無ければ空のリスト" {
            Glob.compile("build.gradle.kts").matchGroups("build.gradle.kts").shouldNotBeNull() shouldContainExactly emptyList()
        }
    }
})
