package me.tbsten.katachi.test.dsl

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.dsl.LayoutDirectory
import me.tbsten.katachi.dsl.LayoutFile
import me.tbsten.katachi.dsl.LayoutScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.dsl.kotlin.ktsFile

/**
 * Whatever katachi puts on top of `LayoutScope`, a project can put there too.
 *
 * `LayoutScope` declares the core vocabulary and nothing else; `ktFile()`, `sourceSet` and
 * the rest are ordinary functions taking the scope as a context parameter. Nothing about
 * that shape is katachi's privilege, so the second half of this spec writes a utility layer
 * the way a project would — in a package that is not katachi's, against the published API —
 * and asserts it declares exactly what writing the paths out declares.
 *
 * The first half is the other side of the same claim: katachi's own utilities have to be
 * *reducible* to the core vocabulary, or "write your own the same way" would be an
 * invitation to write something weaker.
 */
class LayoutUtilityLayerSpec : FreeSpec({
    "ライブラリ自身のユーティリティは core の語彙に還元できる" - {
        "ktFile() は .kt を付けた file() と同じ" {
            layoutOf { "*UseCase".ktFile() }.shape() shouldBe
                layoutOf { "*UseCase.kt".file() }.shape()
        }

        "ktsFile() は .kts を付けた file() と同じ" {
            layoutOf { "build.gradle".ktsFile() }.shape() shouldBe
                layoutOf { "build.gradle.kts".file() }.shape()
        }

        "sourceSet は src/<name> というディレクトリキーと同じ" {
            layoutOf { "commonMain".sourceSet { "Foo".ktFile() } }.shape() shouldBe
                layoutOf { "src/commonMain" { "Foo".ktFile() } }.shape()
        }
    }

    "利用者が自分のユーティリティを同じ形で足せる" - {
        "自前のファイル関数が layout の中で呼べる" {
            layoutOf { "user".protoFile() }.shape() shouldBe
                layoutOf { "user.proto".file() }.shape()
        }

        "自前のディレクトリ関数が / 連結の左辺になる" {
            layoutOf { protoSources() / "user".protoFile() }.shape() shouldBe
                layoutOf { "src/main" / "proto" / "user.proto".file() }.shape()
        }

        "module ブロックの中でも同じように使える" {
            val modules = moduleIndexOf("feature/home", "feature/settings")

            layoutOf(modules) {
                ":feature:*".module { featureSources() / "Screen".ktFile() }
            }.shape() shouldBe layoutOf(modules) {
                ":feature:*".module { mainSourceSet / kotlin / "Screen".ktFile() }
            }.shape()
        }
    }
})

// A project's own utility layer. Written here exactly as it would be written in a user's
// own package: `context(LayoutScope)`, `with(layoutScope)`, and nothing but the published
// vocabulary inside. None of the three could be a member extension of `LayoutScope`,
// because an interface cannot be added to from the outside.

/** `"user".protoFile()` is `user.proto`. */
context(layoutScope: LayoutScope)
private fun String.protoFile(): LayoutFile {
    val name = this
    return with(layoutScope) { "$name.proto".file() }
}

/** Where this project keeps its `.proto` files. */
context(layoutScope: LayoutScope)
private fun protoSources(): LayoutDirectory = with(layoutScope) { mainSourceSet / "proto" }

/** The Kotlin sources of the module being evaluated. */
context(layoutScope: LayoutScope)
private fun featureSources(): LayoutDirectory = with(layoutScope) { mainSourceSet / kotlin }
