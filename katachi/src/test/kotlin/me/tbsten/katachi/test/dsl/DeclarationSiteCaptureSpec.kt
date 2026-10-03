package me.tbsten.katachi.test.dsl

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.architecture

/**
 * `DeclarationSite.capture()`, the public door to the stack capture every DSL call already uses.
 *
 * The expected line is read on the same line as the capture (`Throwable().stackTrace`), so
 * moving a case does not break it.
 *
 * NOTE: このファイルのパッケージを `me.tbsten.katachi.dsl` にしてはいけない。
 * captureDeclarationSite() がライブラリ自身のフレームとして読み飛ばしてしまい、
 * 宣言位置が kotest 内部を指すようになる。
 */
class DeclarationSiteCaptureSpec : FreeSpec({
    "呼んだテストの行を返す" {
        val (site, line) = DeclarationSite.capture() to Throwable().stackTrace.first().lineNumber

        site shouldBe DeclarationSite("DeclarationSiteCaptureSpec.kt", line)
    }

    "テストの中の関数から呼ぶと、その関数の行を返す" {
        val (site, line) = captureHere()

        site shouldBe DeclarationSite("DeclarationSiteCaptureSpec.kt", line)
    }

    "katachi の DSL のブロックの中から呼んでも、ブロックを書いた行を返す" {
        // `architecture { }` runs the block from inside katachi's own frames. Those are skipped,
        // and the first frame outside katachi is this block.
        var captured: Pair<DeclarationSite, Int>? = null
        architecture {
            captured = DeclarationSite.capture() to Throwable().stackTrace.first().lineNumber
        }

        val (site, line) = captured.shouldNotBeNull()
        site shouldBe DeclarationSite("DeclarationSiteCaptureSpec.kt", line)
    }
})

/** A helper a backend might write: the capture names this line, not its caller's. */
private fun captureHere(): Pair<DeclarationSite, Int> =
    DeclarationSite.capture() to Throwable().stackTrace.first().lineNumber
