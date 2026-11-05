package me.tbsten.katachi.test.check

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import me.tbsten.katachi.check.internal.LayoutIndex
import me.tbsten.katachi.check.internal.report
import me.tbsten.katachi.check.validate
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.internal.flattenLayout

/** The role names the index answers for [file]. */
private fun Architecture.rolesOf(file: String): List<String> =
    LayoutIndex(flattenLayout()).rolesOf(file).map { it.name }

/** The nearby locations of [file], as `(role, directory, filePattern)`. */
private fun Architecture.nearbyOf(file: String): List<Triple<String, String, String?>> =
    LayoutIndex(flattenLayout()).nearbyOf(file).map { Triple(it.role.qualifiedName, it.directory, it.filePattern) }

private fun twoRoles(first: String, second: String): Architecture = architectureOf {
    "app".group {
        "First".role { layout { first.file() } }
        "Second".role { layout { second.file() } }
    }
}

/**
 * `?`, `[...]` and `{...}` in the check: the index still finds the roles of a file whose
 * directory pattern holds them, a bound capture's report keeps them as the file has them, and
 * `[UnexpectedFile]` offers places written out from them.
 */
class GlobSyntaxCheckSpec : FreeSpec({
    "索引" - {
        "{..} で始まる段のパターンも索引から落ちない" {
            val arch = architectureOf { "app".group { "Source".role { layout { "src/{main,test}/kotlin/*.kt".file() } } } }

            arch.rolesOf("src/test/kotlin/A.kt") shouldBe listOf("Source")
            arch.rolesOf("src/main/kotlin/A.kt") shouldBe listOf("Source")
            arch.rolesOf("src/debug/kotlin/A.kt").shouldBeEmpty()
        }

        "? や [..] の段も同じ" {
            val arch = architectureOf {
                "app".group {
                    "A".role { layout { "mod?/*.kt".file() } }
                    "B".role { layout { "lib[0-9]/*.kt".file() } }
                }
            }

            arch.rolesOf("mod1/A.kt") shouldBe listOf("A")
            arch.rolesOf("lib7/A.kt") shouldBe listOf("B")
            arch.rolesOf("libx/A.kt").shouldBeEmpty()
        }
    }

    "束縛" - {
        "束縛の比較は空の値も文字列として比べる" {
            val arch = layoutArchitecture { "${capture("n")}-${capture("n")}.kt".file() }

            arch.validate(repositoryOf { "-.kt"() }).labels().shouldBeEmpty()
            arch.validate(repositoryOf { "a-.kt"() }).labels() shouldBe listOf("[CaptureMismatch] a-.kt")
        }

        "期待するパスは ? [..] {..} と名前の無い * をファイルの文字のまま残す" {
            val arch = layoutArchitecture { capture("f") / "{a,b}" / "*-${capture("f")}?.kt".file() }
            val tree = repositoryOf { "x/b" { "zz-y1.kt"() } }

            arch.validate(tree).report() shouldContain "x/b/zz-x1.kt"
        }
    }

    "Nearby" - {
        fun sourceRoles(first: String, second: String = "app/Other.txt") = twoRoles(first, second)

        "{..} の段は選択肢ごとの候補に展開される" {
            val arch = architectureOf { "app".group { "Source".role { layout { "src/{main,test}/kotlin/*.kt".file() } } } }

            arch.nearbyOf("src/x/kotlin/A.kt") shouldBe listOf(
                Triple("app.Source", "src/main/kotlin", "*.kt"),
                Triple("app.Source", "src/test/kotlin", "*.kt"),
            )
        }

        "当たらない ? や [..] の段を含む置き場所は候補にしない" {
            val arch = sourceRoles("src/mod?/*.kt", "src/lib/*.kt")

            arch.nearbyOf("src/xyz/A.kt") shouldBe listOf(Triple("app.Second", "src/lib", "*.kt"))
            arch.nearbyOf("src/mod1/A.kt").first() shouldBe Triple("app.First", "src/mod1", "*.kt")
        }

        "*.{kt,kts} は .kt と .kts のファイルを受けつける" {
            val arch = sourceRoles("src/lib/*.{kt,kts}", "src/other/*.java")

            arch.nearbyOf("src/x/A.kt").map { it.first } shouldBe listOf("app.First")
            arch.nearbyOf("src/x/A.kts").first() shouldBe Triple("app.First", "src/lib", "*.{kt,kts}")
            arch.nearbyOf("src/x/A.java").map { it.first } shouldBe listOf("app.Second")
        }

        "*.k? は .kt を受けつけ .java を受けつけない" {
            val arch = sourceRoles("src/lib/*.k?", "src/other/*.java")

            arch.nearbyOf("src/x/A.kt").map { it.first }.first() shouldBe "app.First"
            arch.nearbyOf("src/x/A.java").map { it.first } shouldBe listOf("app.Second")
        }

        "[A-Z]* は拡張子に関係なく受けつける" {
            val arch = sourceRoles("src/lib/[A-Z]*", "src/other/x.java")

            arch.nearbyOf("src/x/A.md").map { it.first } shouldBe listOf("app.First")
        }
    }
})
