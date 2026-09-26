package com.example.roles

import com.example.forbiddenContents
import me.tbsten.katachi.dsl.DeclarationContainerScope

/**
 * The role of the recorded layout snapshot, which `LayoutSnapshotSpec` writes and compares.
 *
 * The snapshot lives inside the sample, so the allow list has to claim it like anything else
 * that is generated and not under `build/`. This is the same arrangement `GeneratedDocumentation`
 * has, which is why the two sit next to each other.
 */
fun DeclarationContainerScope.layoutSnapshot() = "LayoutSnapshot" {
    title = "レイアウトスナップショット"
    summary = "`layout { }` を平坦化した結果を記録したテキスト。katachi 自身の自己検証用"
    description = """
        この定義の `layout { }` をすべて平坦化し、「役割・パス・種別・必須かどうか」を1行1件で
        並べたテキストです。`LayoutSnapshotSpec` が毎回作り直し、記録済みの内容と突き合わせます。

        役割の書き方を変えたとき、検査する対象が変わっていないことを示すためのものです。
        糖衣（`".".module { }` や `mainSourceSet`）への書き換えは、この差分が空であるかぎり
        安全だと言えます。

        これは手で書かない。`LayoutSnapshotSpec` が書く。意図して変えたときの更新は
        `./gradlew :architecture-test:test --rerun -Dkatachi.snapshot.update=true` です。
        ファイル先頭のコメントにも同じコマンドが書いてあります。

        katachi を導入するプロジェクトには要りません。これは katachi 自身がサンプルを
        壊していないかを見るための仕掛けで、`ProjectArchitectureTest` とは目的が違います。
    """.trimIndent()
    forbiddenContents = """
        - 手で書いた期待値。差分が出たときに直すのは定義側か、スナップショットの再生成の
          どちらかで、テキストを直接編集して辻褄を合わせると番兵の意味が無くなります
        - `.txt` 以外のファイル。いまの `layout { }` は `snapshots/*.txt` しか認めていません
    """.trimIndent()
    example("snapshots/layout.txt", "平坦化したレイアウトの全文")
    layout {
        "snapshots" {
            "*.txt".file()
        }
    }
}
