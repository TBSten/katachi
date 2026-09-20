package me.tbsten.katachi.test.dsl

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.architecture

/**
 * NOTE: このファイルのパッケージを `me.tbsten.katachi.dsl` にしてはいけない。
 * captureDeclarationSite() がライブラリ自身のフレームとして読み飛ばしてしまい、
 * 宣言位置が kotest 内部（FreeSpecRootScope.kt:35）を指すようになる。
 */
class DeclarationSiteSpec : FreeSpec({
    "group・役割・layout それぞれの宣言位置として、ファイル名と行番号が取れる" {
        val arch = architecture {
            "domain".group {
                "UseCase" {
                    layout { }
                }
            }
        }

        // この行番号はファイル内の位置に依存する。上のブロックを1行でも動かしたら直すこと。
        arch.allGroups.single().declaredAt shouldBe DeclarationSite("DeclarationSiteSpec.kt", 16)
        arch.allRoles.single().declaredAt shouldBe DeclarationSite("DeclarationSiteSpec.kt", 17)
        arch.allRoles.single().layouts.single().declaredAt shouldBe
            DeclarationSite("DeclarationSiteSpec.kt", 18)
    }

    "ネストした group の宣言位置も、それぞれ書かれた行を指す" {
        val arch = architecture {
            "domain".group {
                "model".group { }
            }
        }

        // この行番号はファイル内の位置に依存する。
        arch.allGroups.map { it.declaredAt } shouldBe listOf(
            DeclarationSite("DeclarationSiteSpec.kt", 32),
            DeclarationSite("DeclarationSiteSpec.kt", 33),
        )
    }

    "拡張関数で別ファイルに分けた場合、宣言位置はその別ファイルの行を指す" {
        val arch = architecture {
            domainRoles()
        }

        // この行番号は ArchitectureExtensions.kt 内の位置に依存する。
        // あちらに行を足し引きしたらここも直すこと。
        arch.allGroups.map { it.declaredAt } shouldBe listOf(
            DeclarationSite("ArchitectureExtensions.kt", 14),
            DeclarationSite("ArchitectureExtensions.kt", 28),
        )
        arch.allRoles.map { it.declaredAt } shouldBe listOf(
            DeclarationSite("ArchitectureExtensions.kt", 16),
            DeclarationSite("ArchitectureExtensions.kt", 29),
        )
        arch.allRoles.first().layouts.single().declaredAt shouldBe
            DeclarationSite("ArchitectureExtensions.kt", 20)
    }

    "DeclarationSite の toString は ファイル名:行番号 になる" {
        DeclarationSite("ProjectArchitecture.kt", 42).toString() shouldBe "ProjectArchitecture.kt:42"
    }
})
