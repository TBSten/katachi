package me.tbsten.katachi.test.dsl

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.dsl.Documented
import me.tbsten.katachi.dsl.architecture

class DocumentedSpec : FreeSpec({
    "documented を省略した group には Documented が入らない" {
        val arch = architecture { "domain".group { } }
        arch.groups.single()[Documented] shouldBe null
    }

    "documented を省略した役割には Documented が入らない" {
        val arch = architecture { "domain".group { "UseCase" { } } }
        arch.allRoles.single()[Documented] shouldBe null
    }

    "group にはブロック内プロパティで documented = false を指定できる" {
        val arch = architecture {
            "Gradle".group {
                documented = false
                "VersionCatalog" { }
            }
        }
        arch.groups.single()[Documented] shouldBe false
    }

    "役割にはブロック内プロパティで documented = false を指定できる" {
        val arch = architecture {
            "domain".group {
                "UseCase" { documented = false }
                "Repository" { }
            }
        }

        arch.allRoles.map { it.name to (it[Documented] ?: true) } shouldBe
            listOf("UseCase" to false, "Repository" to true)
    }

    "documented = false の group 配下でも、役割の宣言値はそのまま保持される" {
        // 継承・打ち消しの計算はしない（実効値の算出はドキュメント生成側の責務）。
        val arch = architecture {
            "Gradle".group {
                documented = false
                "VersionCatalog" { }
                "BuildScript" { documented = false }
            }
        }

        arch.groups.single()[Documented] shouldBe false
        arch.allRoles.map { it.name to (it[Documented] ?: true) } shouldBe
            listOf("VersionCatalog" to true, "BuildScript" to false)
    }

    "ネストした group の documented も宣言値がそのまま保持される" {
        val arch = architecture {
            "domain".group {
                documented = false
                "model".group { }
            }
        }

        arch.allGroups.map { it.qualifiedName to (it[Documented] ?: true) } shouldBe
            listOf("domain" to false, "domain/model" to true)
    }

    "documented = true を明示した宣言は、書いたとおり Documented に残る" {
        // 省略（Documented が無い）と、明示的な true とを読む側が区別できること。
        val arch = architecture {
            "domain".group {
                documented = true
                "UseCase" { documented = true }
            }
        }

        arch.groups.single()[Documented] shouldBe true
        arch.allRoles.single()[Documented] shouldBe true
    }
})
