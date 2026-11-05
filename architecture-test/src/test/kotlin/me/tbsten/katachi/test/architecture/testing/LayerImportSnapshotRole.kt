package me.tbsten.katachi.test.architecture.testing

import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.div

fun DeclarationContainerScope.layerImportSnapshot() = "LayerImportSnapshot".role {
    title = "層の import スナップショット"
    summary = "processor と check の間の import を 1 行ずつ記録したもの。増えたときに diff で気づくためにある"
    description = """
        `processor` と `check` は同じ層に置いてあるので、層の検査（`LayerImports`）はこの 2 つの間の
        import が増えても止めません。代わりに `LayerImportSnapshotSpec` が両方向の import を 1 行ずつ
        書き出し、コミットされたこのファイルと比べます。

        手で編集しないこと。更新は `./gradlew :architecture-test:test -Dkatachi.snapshot.update=true`。
        `build/` の外にあるのは、diff としてレビューに出すためです。
    """.trimIndent()
    example("architecture-test/snapshots/layer-imports.txt", "processor と check の間の import の一覧")
    layout {
        "architecture-test" / "snapshots" {
            // Named rather than `*.txt`, so the entry is `required` and a deleted snapshot is a
            // `[MissingFile]` instead of an empty allow list.
            "layer-imports.txt".file()
        }
    }
}
