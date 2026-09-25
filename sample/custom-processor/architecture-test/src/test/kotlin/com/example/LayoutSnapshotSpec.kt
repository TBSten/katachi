package com.example

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import java.io.File
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.InternalKatachiApi
import me.tbsten.katachi.dsl.LayoutEntry
import me.tbsten.katachi.dsl.internal.flattenLayout
import me.tbsten.katachi.fs.internal.RealFileSystem
import me.tbsten.katachi.fs.internal.findProjectRoot
import me.tbsten.katachi.scan.internal.moduleIndex

/**
 * katachi's own self-verification, not part of adopting katachi: a sentinel that records what
 * this sample's `layout { }` blocks flatten to, so that rewriting a role is only accepted when
 * it still checks the same tree.
 *
 * The snapshot lives **inside** the sample, at `snapshots/layout.txt`, and is declared by the
 * `testing/LayoutSnapshot` role -- the same arrangement `docs/` has. Everything inside the
 * sample is subject to its own allow list, so a generated file that is not `build/` needs a
 * role; putting it outside the project root would only move that cost somewhere else.
 *
 * `flattenLayout()` is `@InternalKatachiApi` and the [LayoutEntry] it returns is
 * `@ExperimentalKatachiApi` — a user asserts and never reads the entries — so this file opts in
 * to both where a user would not have to.
 */
@OptIn(InternalKatachiApi::class, ExperimentalKatachiApi::class)
class LayoutSnapshotSpec : FreeSpec({
    "平坦化したレイアウトが記録済みのスナップショットと一致する" {
        val actual = renderSnapshot(flattenCurrentLayout())
        val file = snapshotFile()

        when {
            updateRequested() -> file.write(actual)
            !file.isFile -> {
                file.write(actual)
                throw AssertionError(
                    "${file.path} が無かったので今の内容で作成した。差分を確認してから再実行すること。",
                )
            }
            // Compared as one text rather than line by line: the header is generated together
            // with the body, so an update never leaves the two out of step.
            else -> actual shouldBe file.readText()
        }
    }

    "スナップショットが1件以上のエントリを持つ" {
        // A layout that flattened to nothing would match an empty snapshot forever. This is
        // the one thing the comparison above cannot notice by itself.
        flattenCurrentLayout().isNotEmpty() shouldBe true
    }
})

/** Set to `true` to rewrite the snapshot instead of comparing against it. */
private const val UPDATE_PROPERTY: String = "katachi.snapshot.update"

/** Where the snapshot lives, relative to this sample's project root. */
private const val SNAPSHOT_PATH: String = "snapshots/layout.txt"

/**
 * The header written into the snapshot, so the file says how to regenerate itself.
 *
 * `--rerun` is part of it on purpose: the system property is wired into the `test` task as an
 * input, but a test task whose inputs are otherwise unchanged still has to be told to run again.
 */
private val HEADER: List<String> = listOf(
    "# katachi のサンプル自己検証用スナップショット。手で編集しない。",
    "# 生成元: sample/custom-processor の LayoutSnapshotSpec " +
        "(projectArchitecture.flattenLayout() の結果)",
    "# 更新: cd sample/custom-processor && " +
        "./gradlew :architecture-test:test --rerun -D$UPDATE_PROPERTY=true",
    "# 1行 = <役割の qualifiedName> TAB <パス> TAB <種別> TAB <required|optional>",
)

private fun updateRequested(): Boolean = System.getProperty(UPDATE_PROPERTY) == "true"

private fun File.write(text: String) {
    parentFile.mkdirs()
    writeText(text)
}

/**
 * The layout as the check sees it.
 *
 * The module index is built from the real tree, because this definition names modules with
 * `":".module { }` and `":architecture-test".module { }` and those keys are what it resolves.
 * Recording the snapshot without it would compare two different questions.
 */
@OptIn(InternalKatachiApi::class, ExperimentalKatachiApi::class)
private fun flattenCurrentLayout(): List<LayoutEntry> {
    val fileSystem = RealFileSystem()
    val projectRoot = findProjectRoot(fileSystem)
    val modules = moduleIndex(fileSystem, projectRoot.path, projectArchitecture.moduleResolver)
    return projectArchitecture.flattenLayout(modules)
}

/**
 * Renders the entries in an order that depends on nothing but the entries themselves.
 *
 * Declaration order is deliberately dropped: reordering the roles changes nothing about what
 * the check accepts.
 */
@OptIn(InternalKatachiApi::class, ExperimentalKatachiApi::class)
private fun renderSnapshot(entries: List<LayoutEntry>): String {
    val lines = entries
        .map { entry ->
            val requirement = if (entry.required) "required" else "optional"
            "${entry.role.qualifiedName}\t${entry.path}\t${entry.kind}\t$requirement"
        }
        .sorted()
    return (HEADER + lines).joinToString(separator = "\n", postfix = "\n")
}

/**
 * `sample/custom-processor/snapshots/layout.txt`, located through katachi's own project-root
 * detection rather than by counting `..` from the working directory.
 */
@OptIn(InternalKatachiApi::class, ExperimentalKatachiApi::class)
private fun snapshotFile(): File =
    File(File(findProjectRoot(RealFileSystem()).path.value), SNAPSHOT_PATH)
