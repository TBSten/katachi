package me.tbsten.katachi.test.scan

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.dsl.LayoutEntry
import me.tbsten.katachi.dsl.LayoutScope
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.flattenLayout
import me.tbsten.katachi.dsl.gradle.module
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.scan.AmbiguousLayout
import me.tbsten.katachi.scan.ambiguousLayoutsOf

/**
 * `ambiguousLayoutsOf`, the declaration-only half of step 5-2: what two roles' `layout { }`
 * blocks say by themselves, with no walk of a real tree involved. `AmbiguousLayoutCheckSpec`
 * covers the same detector wired through `validate()` / `assert()` against a fake file system.
 */
private fun twoRoles(
    roleA: LayoutScope.() -> Unit,
    roleB: LayoutScope.() -> Unit,
): List<LayoutEntry> = architecture {
    "group".group {
        "RoleA" { layout(roleA) }
        "RoleB" { layout(roleB) }
    }
}.flattenLayout()

private fun threeRoles(
    roleA: LayoutScope.() -> Unit,
    roleB: LayoutScope.() -> Unit,
    roleC: LayoutScope.() -> Unit,
): List<LayoutEntry> = architecture {
    "group".group {
        "RoleA" { layout(roleA) }
        "RoleB" { layout(roleB) }
        "RoleC" { layout(roleC) }
    }
}.flattenLayout()

class AmbiguousLayoutSpec : FreeSpec({
    "完全重複" - {
        "同じ path を2つの役割が宣言すると1件の AmbiguousLayout になる" {
            val entries = twoRoles(
                { "gradle" / "libs.versions.toml".file() },
                { "gradle" / "libs.versions.toml".file() },
            )

            val warnings = ambiguousLayoutsOf(entries).filterIsInstance<AmbiguousLayout>()

            warnings.map { it.path } shouldBe listOf("gradle/libs.versions.toml")
        }

        "claims は役割の宣言順に並ぶ" {
            val entries = twoRoles(
                { "gradle" / "libs.versions.toml".file() },
                { "gradle" / "libs.versions.toml".file() },
            )

            val claims = ambiguousLayoutsOf(entries).filterIsInstance<AmbiguousLayout>().single().claims

            claims.map { it.role.qualifiedName } shouldBe listOf("group/RoleA", "group/RoleB")
        }

        "3つ以上の役割が同じ path を宣言すると claims が3件になる" {
            val entries = threeRoles(
                { "docs" / "README.md".file() },
                { "docs" / "README.md".file() },
                { "docs" / "README.md".file() },
            )

            val claims = ambiguousLayoutsOf(entries).filterIsInstance<AmbiguousLayout>().single().claims

            claims.map { it.role.qualifiedName } shouldBe listOf("group/RoleA", "group/RoleB", "group/RoleC")
        }

        "1つの役割しか宣言していなければ警告にならない" {
            val entries = twoRoles(
                { "gradle" / "libs.versions.toml".file() },
                { "docs" / "README.md".file() },
            )

            ambiguousLayoutsOf(entries).shouldBeEmpty()
        }

        "path のテキストが違えば警告にならない" {
            val entries = twoRoles(
                { ":feature:*".module { "Screen".ktFile() } },
                { "feature/home" { "Screen".ktFile() } },
            )

            ambiguousLayoutsOf(entries).shouldBeEmpty()
        }
    }

    "Directory / Ignore は対象外" - {
        "同じディレクトリを通るだけで、宣言するファイル名が違えば警告にならない" {
            // Both roles pass through `core/domain` — the Directory entry for it is declared by
            // both — but they name different files below it, so nothing is actually ambiguous.
            val entries = twoRoles(
                { "core/domain" { "*UseCase".ktFile() } },
                { "core/domain" { "*Repository".ktFile() } },
            )

            ambiguousLayoutsOf(entries).shouldBeEmpty()
        }

        "2つの役割が同じディレクトリを ignore() しても警告にならない" {
            // Hand written, not synthetic: still excluded, because `ignore()` grants no role
            // ownership over what sits inside it (`LayoutIndex.rolesOf` never reads it).
            val entries = twoRoles(
                { "secrets".ignore() },
                { "secrets".ignore() },
            )

            ambiguousLayoutsOf(entries).shouldBeEmpty()
        }
    }

    "module { } の定型" - {
        "2つの役割が同じモジュールを .module { } で書いても build / build.gradle.kts に警告は出ない" {
            val entries = twoRoles(
                { ":app".module { } },
                { ":app".module { } },
            )

            ambiguousLayoutsOf(entries).shouldBeEmpty()
        }

        "両方の役割が build.gradle.kts を自分で書くと、その path にだけ警告が出る" {
            val entries = twoRoles(
                { ":app".module { "build.gradle.kts".file() } },
                { ":app".module { "build.gradle.kts".file() } },
            )

            val warnings = ambiguousLayoutsOf(entries).filterIsInstance<AmbiguousLayout>()

            warnings.map { it.path } shouldBe listOf("app/build.gradle.kts")
        }

        "片方だけが build.gradle.kts を自分で書いても警告にならない" {
            // The other role's copy is still the sugar's own declaration, so after dropping
            // synthetic entries only one role's claim is left on this path.
            val entries = twoRoles(
                { ":app".module { "build.gradle.kts".file() } },
                { ":app".module { } },
            )

            ambiguousLayoutsOf(entries).shouldBeEmpty()
        }
    }
})
