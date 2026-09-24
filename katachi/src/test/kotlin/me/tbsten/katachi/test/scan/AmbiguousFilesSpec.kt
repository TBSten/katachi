package me.tbsten.katachi.test.scan

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.dsl.ArchitectureScope
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.flattenLayout
import me.tbsten.katachi.dsl.gradle.module
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.scan.AmbiguousLayout
import me.tbsten.katachi.scan.FileOverlap
import me.tbsten.katachi.scan.FileOverlaps
import me.tbsten.katachi.scan.LayoutIndex
import me.tbsten.katachi.scan.ambiguousFilesOf
import me.tbsten.katachi.scan.ambiguousLayoutsOf

/**
 * `FileOverlaps` and `ambiguousFilesOf`, the half of step 5-2 that needs the files to exist:
 * what two roles' patterns turn out to select, rather than what their text says.
 *
 * Driven with a hand written list of paths standing in for a walk, so that the grouping and
 * the deduplication are pinned without a file system. `AmbiguousLayoutCheckSpec` covers the
 * same detector wired through `validate()` against a fake tree.
 */
private fun overlapsOf(vararg files: String, block: ArchitectureScope.() -> Unit): List<FileOverlap> {
    val index = LayoutIndex(architecture(block).flattenLayout())
    val overlaps = FileOverlaps()
    for (file in files) overlaps.record(file, index.claimsOn(file))
    return overlaps.toList()
}

class AmbiguousFilesSpec : FreeSpec({
    "まとめかた" - {
        "違う pattern が同じファイルに当たると1グループになる" {
            val overlaps = overlapsOf("home/HomeScreen.kt", "home/HomeViewModel.kt") {
                "ui".group {
                    "Component" { layout { "home" { "*".ktFile() } } }
                    "ViewModel" { layout { "home" { "*ViewModel".ktFile() } } }
                }
            }

            overlaps.single().files shouldBe listOf("home/HomeViewModel.kt")
        }

        "同じ役割の組なら、重なったファイルが何個あっても1グループにまとまる" {
            val overlaps = overlapsOf(
                "home/HomeViewModel.kt",
                "search/SearchViewModel.kt",
                "topic/TopicViewModel.kt",
            ) {
                "ui".group {
                    "Component" { layout { "*" { "*".ktFile() } } }
                    "ViewModel" { layout { "*" { "*ViewModel".ktFile() } } }
                }
            }

            overlaps.single().files shouldBe listOf(
                "home/HomeViewModel.kt",
                "search/SearchViewModel.kt",
                "topic/TopicViewModel.kt",
            )
        }

        "役割の組が違えばグループも分かれる" {
            val overlaps = overlapsOf("home/HomeViewModel.kt", "home/HomeState.kt") {
                "ui".group {
                    "Component" { layout { "home" { "*".ktFile() } } }
                    "ViewModel" { layout { "home" { "*ViewModel".ktFile() } } }
                    "State" { layout { "home" { "*State".ktFile() } } }
                }
            }

            overlaps.map { overlap -> overlap.claims.map { it.role.name } } shouldBe
                listOf(listOf("Component", "ViewModel"), listOf("Component", "State"))
        }

        "1つの役割しか掴んでいないファイルはグループを作らない" {
            val overlaps = overlapsOf("home/HomeScreen.kt") {
                "ui".group {
                    "Component" { layout { "home" { "*".ktFile() } } }
                    "ViewModel" { layout { "home" { "*ViewModel".ktFile() } } }
                }
            }

            overlaps.shouldBeEmpty()
        }
    }

    "所有を主張しない宣言" - {
        "module { } が注入する build.gradle.kts は重なり扱いにならない" {
            val overlaps = overlapsOf("app/build.gradle.kts") {
                "app".group {
                    "Foo" { layout { ":app".module { "Foo".ktFile() } } }
                    "Bar" { layout { ":app".module { "Bar".ktFile() } } }
                }
            }

            overlaps.shouldBeEmpty()
        }

        "両方の役割が build.gradle.kts を自分で書いたときは重なりになる" {
            val overlaps = overlapsOf("app/build.gradle.kts") {
                "app".group {
                    "Foo" { layout { ":app".module { "build.gradle.kts".file() } } }
                    "Bar" { layout { ":app".module { "build.gradle.kts".file() } } }
                }
            }

            overlaps.single().files shouldBe listOf("app/build.gradle.kts")
        }

        "anyFile() は claim なので file() と重なる" {
            val overlaps = overlapsOf("generated/Api.kt") {
                "build".group {
                    "Open" { layout { "generated" { anyFile() } } }
                    "Kotlin" { layout { "generated" { "*".ktFile() } } }
                }
            }

            // The role that named the file comes first, the one that only opened the
            // directory second — see `LayoutIndex.claimsOn`.
            overlaps.single().claims.map { it.role.name } shouldBe listOf("Kotlin", "Open")
        }
    }

    "二重報告の抑止" - {
        "path のテキストが一致して既に報告済みの役割の組は落とされる" {
            val definition = architecture {
                "ui".group {
                    "Component" { layout { "home" { "*".ktFile() } } }
                    "ViewModel" { layout { "home" { "*".ktFile() } } }
                }
            }
            val entries = definition.flattenLayout()
            val index = LayoutIndex(entries)
            val overlaps = FileOverlaps().apply {
                record("home/HomeScreen.kt", index.claimsOn("home/HomeScreen.kt"))
            }

            ambiguousFilesOf(overlaps.toList(), ambiguousLayoutsOf(entries)).shouldBeEmpty()
        }

        "報告済みの組が1つも無ければ、そのまま AmbiguousLayout になる" {
            val overlaps = overlapsOf("home/HomeViewModel.kt", "home/OtherViewModel.kt") {
                "ui".group {
                    "Component" { layout { "home" { "*".ktFile() } } }
                    "ViewModel" { layout { "home" { "*ViewModel".ktFile() } } }
                }
            }

            val warning: AmbiguousLayout = ambiguousFilesOf(overlaps, declared = emptyList()).single()

            warning.path shouldBe "home/HomeViewModel.kt"
            warning.overlappingFiles shouldBe listOf("home/HomeViewModel.kt", "home/OtherViewModel.kt")
        }
    }
})
