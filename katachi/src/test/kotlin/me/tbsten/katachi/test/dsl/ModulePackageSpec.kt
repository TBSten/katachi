package me.tbsten.katachi.test.dsl

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import me.tbsten.katachi.dsl.HyphenFolding
import me.tbsten.katachi.dsl.InternalKatachiApi
import me.tbsten.katachi.dsl.KatachiDeclarationException
import me.tbsten.katachi.dsl.ModulePackage
import me.tbsten.katachi.dsl.ModulePackageException
import me.tbsten.katachi.dsl.capitalizedModuleNamePackage
import me.tbsten.katachi.dsl.moduleNamePackage
import me.tbsten.katachi.dsl.resolveFor

/** モジュールパス -> 既定の変換結果。module-package.md の変換規則の表。 */
private val capitalized = listOf(
    ":hoge:fuga-piyo" to "hoge/fugaPiyo",
    ":core:data:remote-api" to "core/data/remoteApi",
    ":feature:debug-menu" to "feature/debugMenu",
)

/** モジュールパス -> 小文字連結オプションでの変換結果。 */
private val concatenated = listOf(
    ":hoge:fuga-piyo" to "hoge/fugapiyo",
    ":core:data:remote-api" to "core/data/remoteapi",
    ":feature:debug-menu" to "feature/debugmenu",
)

@OptIn(InternalKatachiApi::class)
class ModulePackageSpec : FreeSpec({
    "capitalizedModuleNamePackage" - {
        capitalized.forEach { (modulePath, expected) ->
            "$modulePath は $expected に解決される" {
                capitalizedModuleNamePackage().resolveFor(modulePath) shouldBe expected
            }
        }

        "階層はそのまま写される" {
            capitalizedModuleNamePackage().resolveFor(":core:data:remoteApi") shouldBe
                "core/data/remoteApi"
        }

        "先頭の : が無くても同じディレクトリに解決される" {
            val modulePackage = capitalizedModuleNamePackage()
            modulePackage.resolveFor("core:data") shouldBe modulePackage.resolveFor(":core:data")
        }

        "moduleNamePackage の既定と同じ結果になる" {
            val preset = capitalizedModuleNamePackage("com.example")
            val general = moduleNamePackage("com.example", HyphenFolding.Capitalize)
            preset.resolveFor(":feature:debug-menu") shouldBe
                general.resolveFor(":feature:debug-menu")
        }
    }

    "小文字連結オプション" - {
        concatenated.forEach { (modulePath, expected) ->
            "$modulePath は $expected に解決される" {
                moduleNamePackage(hyphens = HyphenFolding.Concatenate)
                    .resolveFor(modulePath) shouldBe expected
            }
        }
    }

    "ベース package" - {
        "ドット区切りで渡すとその配下に解決される" {
            capitalizedModuleNamePackage("com.example").resolveFor(":hoge:fuga-piyo") shouldBe
                "com/example/hoge/fugaPiyo"
        }

        "スラッシュ区切りでも同じ結果になる" {
            capitalizedModuleNamePackage("com/example").resolveFor(":core:domain") shouldBe
                "com/example/core/domain"
        }

        "省略するとモジュールパスだけが package になる" {
            capitalizedModuleNamePackage().resolveFor(":core:domain") shouldBe "core/domain"
        }

        "ルートプロジェクト : はベース package そのものに解決される" {
            capitalizedModuleNamePackage("com.example").resolveFor(":") shouldBe "com/example"
        }
    }

    "照合するのはディレクトリ名だけ" - {
        listOf(
            ":core:in" to "core/in",
            ":core:object" to "core/object",
            ":core:2fa" to "core/2fa",
        ).forEach { (modulePath, expected) ->
            "予約語や数字始まりの $modulePath もそのまま $expected に解決される" {
                capitalizedModuleNamePackage().resolveFor(modulePath) shouldBe expected
            }
        }

        "アンダースコア・大文字・数字は変換されない" {
            capitalizedModuleNamePackage().resolveFor(":Core:remote_api2") shouldBe
                "Core/remote_api2"
        }

        "ハイフンの後ろが数字でも大文字にはならない" {
            capitalizedModuleNamePackage().resolveFor(":auth:login-2fa") shouldBe "auth/login2fa"
        }
    }

    "モジュールごとの解決" - {
        "同じ val が :core:domain と :feature:home で別のディレクトリに解決される" {
            val modulePackage = capitalizedModuleNamePackage("com.example")

            modulePackage.resolveFor(":core:domain") shouldBe "com/example/core/domain"
            modulePackage.resolveFor(":feature:home") shouldBe "com/example/feature/home"
        }

        "生成しただけでは導出が走らない" {
            var derived = 0
            val modulePackage = ModulePackage { modulePath ->
                derived++
                "com/example/$modulePath"
            }

            derived shouldBe 0
            modulePackage.resolveFor("home")
            derived shouldBe 1
        }
    }

    "利用者が自前の導出戦略を書ける" {
        // 階層を省く流儀: :core:data も :feature:home も com.example の直下に置く。
        val modulePackage = ModulePackage { modulePath ->
            "com/example/" + modulePath.substringAfterLast(':')
        }

        modulePackage.resolveFor(":core:data") shouldBe "com/example/data"
        modulePackage.resolveFor(":feature:home") shouldBe "com/example/home"
    }

    "モジュール外での使用" - {
        "モジュールが無い場所で使うと失敗する" {
            val thrown = shouldThrow<ModulePackageException> {
                capitalizedModuleNamePackage("com.example").resolveFor(null)
            }

            thrown.message!!.shouldContain("inside a module block")
            thrown.message!!.shouldContain(".module { }")
        }

        "ModulePackageException は KatachiDeclarationException として捕捉できる" {
            shouldThrow<KatachiDeclarationException> {
                capitalizedModuleNamePackage().resolveFor(null)
            }.shouldBeInstanceOf<ModulePackageException>()
        }

        "ModulePackageException は IllegalArgumentException として捕捉できる" {
            shouldThrow<IllegalArgumentException> {
                capitalizedModuleNamePackage().resolveFor(null)
            }.shouldBeInstanceOf<ModulePackageException>()
        }
    }

    "ディレクトリにならない導出結果" - {
        "ベース package の無いルートプロジェクトは失敗する" {
            val thrown = shouldThrow<ModulePackageException> {
                capitalizedModuleNamePackage().resolveFor(":")
            }

            thrown.message!!.shouldContain("`:`")
            thrown.message!!.shouldContain("capitalizedModuleNamePackage(\"com.example\")")
        }

        "空の階層を返す戦略は失敗する" {
            val thrown = shouldThrow<ModulePackageException> {
                ModulePackage { "com//example" }.resolveFor(":core:domain")
            }

            thrown.message!!.shouldContain("`com//example`")
        }
    }
})
