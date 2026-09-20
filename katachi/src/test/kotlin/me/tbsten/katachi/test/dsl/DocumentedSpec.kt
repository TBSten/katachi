package me.tbsten.katachi.test.dsl

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.dsl.architecture

class DocumentedSpec : FreeSpec({
    "documented を省略した group は true になる" {
        val arch = architecture { "domain".group { } }
        arch.groups.single().documented shouldBe true
    }

    "documented を省略した役割は true になる" {
        val arch = architecture { "domain".group { "UseCase" { } } }
        arch.allRoles.single().documented shouldBe true
    }

    "group には引数で documented = false を指定できる" {
        val arch = architecture {
            "Gradle".group(documented = false) {
                "VersionCatalog" { }
            }
        }
        arch.groups.single().documented shouldBe false
    }

    "役割にはブロック内プロパティで documented = false を指定できる" {
        val arch = architecture {
            "domain".group {
                "UseCase" { documented = false }
                "Repository" { }
            }
        }

        arch.allRoles.map { it.name to it.documented } shouldBe
            listOf("UseCase" to false, "Repository" to true)
    }

    "documented = false の group 配下でも、役割の宣言値はそのまま保持される" {
        // 継承・打ち消しの計算はしない（実効値の算出はドキュメント生成側の責務）。
        val arch = architecture {
            "Gradle".group(documented = false) {
                "VersionCatalog" { }
                "BuildScript" { documented = false }
            }
        }

        arch.groups.single().documented shouldBe false
        arch.allRoles.map { it.name to it.documented } shouldBe
            listOf("VersionCatalog" to true, "BuildScript" to false)
    }

    "ネストした group の documented も宣言値がそのまま保持される" {
        val arch = architecture {
            "domain".group(documented = false) {
                "model".group { }
            }
        }

        arch.allGroups.map { it.qualifiedName to it.documented } shouldBe
            listOf("domain" to false, "domain/model" to true)
    }
})
