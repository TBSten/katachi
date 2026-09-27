package com.example.kmp

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import java.io.File
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.InternalKatachiApi
import me.tbsten.katachi.check.internal.moduleIndex
import me.tbsten.katachi.dsl.LayoutEntry
import me.tbsten.katachi.dsl.files.internal.RealFileSystem
import me.tbsten.katachi.dsl.files.internal.findProjectRoot
import me.tbsten.katachi.dsl.internal.flattenLayout

/**
 * katachi's own self-verification, not part of adopting katachi: a sentinel that pins down
 * which tree this sample's layout checks, so that rewriting the definition shows up as a diff.
 *
 * The definition leans on shorthand -- `.module { }`, `mainSourceSet`, `modulePackage`,
 * `gradle()` -- and whether a rewrite still checks the same files cannot be read off the
 * source. So the flattened layout is recorded as text and compared on every run: a rewrite
 * that says the same thing in other words keeps this green, one that checks a different tree
 * does not, and a change to what katachi's own shorthand expands to lands here as a diff too.
 *
 * `flattenLayout()` is `@InternalKatachiApi` and the [LayoutEntry] it returns is
 * `@ExperimentalKatachiApi` — a user asserts and never reads the entries — so this file opts
 * in to both where a user would not have to.
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
            // Compared as one text rather than line by line: the header is generated
            // together with the body, so an update never leaves the two out of step.
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

/**
 * Where this sample keeps its snapshot, below its own project root.
 *
 * Inside `sample/kmp`, and so subject to this sample's own allow list — which is the point
 * rather than a price. The sample's `testing/LayoutSnapshot` role claims
 * `snapshots/layout.txt` by name, so a snapshot deleted or renamed fails the check instead of
 * quietly going missing, and the generated `docs/testing/LayoutSnapshot.md` is where a reader
 * is told what this file is and how to regenerate it. Declaring it costs one role, the same
 * one the generated `docs/` needed when it moved into the sample.
 */
private const val SNAPSHOT_DIRECTORY_NAME: String = "snapshots"

/** The one snapshot this sample records, declared under [SNAPSHOT_DIRECTORY_NAME] by name. */
private const val SNAPSHOT_FILE_NAME: String = "layout.txt"

/**
 * The header written into the snapshot, so the file says how to regenerate itself.
 */
private val HEADER: List<String> = listOf(
    "# katachi のサンプル自己検証用スナップショット。手で編集しない。",
    "# 生成元: sample/kmp の LayoutSnapshotSpec (projectArchitecture.flattenLayout() の結果)",
    "# 更新: cd sample/kmp && ./gradlew :architecture-test:test -D$UPDATE_PROPERTY=true",
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
 * The module index is built from the real tree, exactly as a check that knows about Gradle
 * modules has to: it is what resolves keys like `":app".module { }` and `":**".module { }`
 * into directories. Recording the snapshot without it would compare two different questions.
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
 * Declaration order is deliberately dropped: reordering the roles changes nothing about
 * what the check accepts, and a refactoring may well move a declaration from one place to
 * another.
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
 * `sample/kmp/snapshots/layout.txt`, located through katachi's own project-root detection.
 *
 * Reusing [findProjectRoot] rather than counting `..` from the working directory means the
 * lookup is guarded by [ProjectRootSpec] like everything else that depends on the root.
 */
@OptIn(InternalKatachiApi::class, ExperimentalKatachiApi::class)
private fun snapshotFile(): File {
    val projectRoot = File(findProjectRoot(RealFileSystem()).path.value)
    return File(projectRoot, "$SNAPSHOT_DIRECTORY_NAME/$SNAPSHOT_FILE_NAME")
}
