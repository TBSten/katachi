package me.tbsten.katachi.test.dsl

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.dsl.camelCase
import me.tbsten.katachi.dsl.flatCase
import me.tbsten.katachi.dsl.kebabCase
import me.tbsten.katachi.dsl.nameWords
import me.tbsten.katachi.dsl.pascalCase
import me.tbsten.katachi.dsl.screamingSnakeCase
import me.tbsten.katachi.dsl.snakeCase

class NamingSpec : FreeSpec({
    "語の切り出し" - {
        "ハイフンで区切る" {
            "debug-menu".nameWords shouldContainExactly listOf("debug", "menu")
        }

        "アンダースコアでも区切る" {
            "debug_menu".nameWords shouldContainExactly listOf("debug", "menu")
        }

        "小文字のあとの大文字で区切る" {
            "debugMenu".nameWords shouldContainExactly listOf("debug", "menu")
        }

        "大文字が続いたあとに小文字が来たら、その大文字から次の語が始まる" {
            "APIClient".nameWords shouldContainExactly listOf("api", "client")
        }

        "語末の大文字の連なりは1語にまとめる" {
            "remoteAPI".nameWords shouldContainExactly listOf("remote", "api")
        }

        "数字は区切りにならない" {
            "2fa".nameWords shouldContainExactly listOf("2fa")
            "v2".nameWords shouldContainExactly listOf("v2")
        }

        "数字のあとの大文字は区切りになる" {
            "v2Api".nameWords shouldContainExactly listOf("v2", "api")
        }

        "区切りが続いても空の語は生まれない" {
            "debug--menu".nameWords shouldContainExactly listOf("debug", "menu")
            "-debug-".nameWords shouldContainExactly listOf("debug")
        }

        "語が1つも無ければ空になる" {
            "".nameWords shouldContainExactly emptyList()
            "--".nameWords shouldContainExactly emptyList()
        }
    }

    "pascalCase" - {
        "\"debug-menu\" は \"DebugMenu\" になる" {
            "debug-menu".pascalCase shouldBe "DebugMenu"
        }

        "すでに PascalCase なら変わらない" {
            "DebugMenu".pascalCase shouldBe "DebugMenu"
        }

        "大文字の連なりも語として畳む" {
            "remoteAPI".pascalCase shouldBe "RemoteApi"
        }

        "何度掛けても結果が変わらない" {
            "debug-menu".pascalCase.pascalCase shouldBe "DebugMenu"
        }

        "数字で始まる語は大文字にできないのでそのまま残る" {
            "2fa".pascalCase shouldBe "2fa"
        }

        "語が無ければ空文字列になる" {
            "".pascalCase shouldBe ""
        }
    }

    "そのほかの綴り" - {
        "camelCase は先頭の語だけ小文字のままにする" {
            "debug-menu".camelCase shouldBe "debugMenu"
            "DebugMenu".camelCase shouldBe "debugMenu"
        }

        "kebabCase はハイフンで繋ぐ" {
            "debugMenu".kebabCase shouldBe "debug-menu"
        }

        "snakeCase はアンダースコアで繋ぐ" {
            "debugMenu".snakeCase shouldBe "debug_menu"
        }

        "screamingSnakeCase は大文字にしてアンダースコアで繋ぐ" {
            "debugMenu".screamingSnakeCase shouldBe "DEBUG_MENU"
        }

        "flatCase は区切りを入れずに小文字で繋ぐ" {
            "debug-menu".flatCase shouldBe "debugmenu"
        }
    }

    "モジュール名とファイル名を連動させる" - {
        "捕捉したモジュール名からファイル名を組み立てられる" {
            val featureName = "debug-menu"
            "${featureName.pascalCase}Screen.kt" shouldBe "DebugMenuScreen.kt"
        }
    }
})
