package com.example.sample

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import java.io.File
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.InternalKatachiApi
import me.tbsten.katachi.check.RealFileSystem
import me.tbsten.katachi.check.findProjectRoot
import me.tbsten.katachi.check.moduleIndex
import me.tbsten.katachi.dsl.LayoutEntry
import me.tbsten.katachi.dsl.flattenLayout

/**
 * katachi's own self-verification, not part of adopting katachi: a sentinel that pins down
 * what this sample's layout means while step 3 rewrites it with `.module { }`,
 * `mainSourceSet` and `modulePackage`.
 *
 * Step 3 is accepted only if the sugar says exactly what the plain directories said, and
 * that cannot be shown after the fact. So the flattened layout of the step 2 definition is
 * recorded as text and compared on every run: rewriting a role with sugar keeps this green,
 * rewriting it into something that checks a different tree does not.
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
 * Where the snapshots live, as a directory next to the sample project roots.
 *
 * Deliberately **outside** `sample/android`, because everything inside it is subject to this
 * sample's own allow list, and no existing role claims a snapshot file. Putting it in
 * `architecture-test/src/test/resources` would have meant editing a role definition — the
 * very thing this sentinel exists to keep still.
 */
private const val SNAPSHOT_DIRECTORY_NAME: String = "layout-snapshots"

/**
 * The header written into the snapshot, so the file says how to regenerate itself.
 *
 * `--rerun` is part of it on purpose: the system property is wired into the `test` task as
 * an input, but a test task whose inputs are otherwise unchanged still has to be told to
 * run again.
 */
private val HEADER: List<String> = listOf(
    "# katachi のサンプル自己検証用スナップショット。手で編集しない。",
    "# 生成元: sample/android の LayoutSnapshotSpec (projectArchitecture.flattenLayout() の結果)",
    "# 更新: cd sample/android && ./gradlew :architecture-test:test --rerun -D$UPDATE_PROPERTY=true",
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
 * modules has to: with the step 2 definition no key names a module, so the index changes
 * nothing, and once step 3 rewrites the keys as `":app".module { }` it is what resolves
 * them. Recording the snapshot without it would compare two different questions.
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
 * what the check accepts, and step 3 may well move a declaration from one place to another.
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
 * `sample/layout-snapshots/android.txt`, located through katachi's own project-root detection.
 *
 * Reusing [findProjectRoot] rather than counting `..` from the working directory means the
 * lookup is guarded by [ProjectRootSpec] like everything else that depends on the root.
 */
@OptIn(InternalKatachiApi::class, ExperimentalKatachiApi::class)
private fun snapshotFile(): File {
    val projectRoot = File(findProjectRoot(RealFileSystem()).path.value)
    val samplesDirectory = requireNotNull(projectRoot.parentFile) {
        "$projectRoot の親ディレクトリが取れない"
    }
    return File(samplesDirectory, "$SNAPSHOT_DIRECTORY_NAME/${projectRoot.name}.txt")
}
