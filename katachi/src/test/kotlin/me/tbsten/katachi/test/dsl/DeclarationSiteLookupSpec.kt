package me.tbsten.katachi.test.dsl

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.ArchitectureScope
import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.architecture
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/**
 * The line of the caller, read the slow and obvious way. Serves as the oracle: a declaration
 * written on the same line as `callerLine()` must report that line.
 *
 * Must not be inline, so that frame 1 is the caller with a real line number. Must not be
 * private either: a lambda calling a private top-level function goes through a synthetic
 * accessor, and frame 1 would then be that accessor at line 1.
 */
internal fun callerLine(): Int = Throwable().stackTrace[1].lineNumber

private const val FILE = "DeclarationSiteLookupSpec.kt"

/** Declares one group [depth] frames below the caller, and returns the architecture plus the line. */
private fun declareAtDepth(depth: Int): Pair<Architecture, Int> =
    if (depth == 0) {
        var line = -1
        val arch = architecture { "deep".group { }; line = callerLine() }
        arch to line
    } else {
        declareAtDepth(depth - 1)
    }

/** A user helper: the site must be the line in here, not the line that calls the helper. */
private fun ArchitectureScope.helperGroup(): Int {
    "helper".group { }; return callerLine()
}

/**
 * NOTE: このファイルのパッケージを `me.tbsten.katachi.dsl` にしてはいけない。
 * captureDeclarationSite() がライブラリ自身のフレームとして読み飛ばしてしまう。
 *
 * 宣言位置は「katachi の外にある、いちばん新しいフレーム」で決まる。スタックの深さ、呼ぶスレッド、
 * 同じ行を何度評価するかに左右されないことを固定する。
 */
class DeclarationSiteLookupSpec : FreeSpec({
    "深い再帰の底で宣言しても、いちばん近い利用者の行を指す" {
        listOf(0, 50, 2_000).forEach { depth ->
            val (arch, line) = declareAtDepth(depth)
            arch.allGroups.single().declaredAt shouldBe DeclarationSite(FILE, line)
        }
    }

    "利用者のヘルパー関数から宣言すると、ヘルパーの中の行を指す" {
        var line = -1
        val arch = architecture { line = helperGroup() }

        arch.allGroups.single().declaredAt shouldBe DeclarationSite(FILE, line)
    }

    "別のスレッドで宣言しても、そのスレッドで書いた行を指す" {
        val result = AtomicReference<Pair<Architecture, Int>>()
        thread {
            var line = -1
            val arch = architecture { "worker".group { }; line = callerLine() }
            result.set(arch to line)
        }.join()

        val (arch, line) = result.get()
        arch.allGroups.single().declaredAt shouldBe DeclarationSite(FILE, line)
    }

    "同じ行を何度評価しても、毎回同じ位置になる" {
        val sites = (1..3).map {
            var line = -1
            val arch = architecture { "again".group { }; line = callerLine() }
            arch.allGroups.single().declaredAt to line
        }

        sites.map { it.first }.distinct().size shouldBe 1
        sites.forEach { (site, line) -> site shouldBe DeclarationSite(FILE, line) }
    }

    "役割・layout の宣言と、layout の中の宣言も、書いた行を指す" {
        var roleLine = -1
        var layoutLine = -1
        var entryLine = -1
        val arch = architecture {
            "g".group {
                "Role" {
                    roleLine = callerLine() - 1
                    layout { }; layoutLine = callerLine()
                }
            }
        }

        arch.allRoles.single().declaredAt shouldBe DeclarationSite(FILE, roleLine)
        arch.allRoles.single().layouts.single().declaredAt shouldBe DeclarationSite(FILE, layoutLine)
        layoutOf { ".gitignore".file(); entryLine = callerLine() }.single().declaredAt shouldBe
            DeclarationSite(FILE, entryLine)
    }
})
