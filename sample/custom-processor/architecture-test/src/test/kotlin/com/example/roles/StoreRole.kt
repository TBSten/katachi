package com.example.roles

import com.example.allowedContents
import com.example.forbiddenContents
import com.example.modulePackage
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/** The role of where the values come from. */
fun DeclarationContainerScope.store() = "Store" {
    title = "保管庫"
    summary = "値がどこから来るかを引き受ける。いまはメモリ上の固定値"
    description = """
        モデルをどこから持ってくるかを引き受ける場所です。`NoteStore` は決め打ちの2件を
        返すだけですが、保存先がファイルやデータベースに変わってもエントリポイントと
        モデルは書き換わらない、という境界をここに引いています。

        `layout { }` は `store` パッケージ直下の `.kt` を認めます。ファイル名は縛っていないので、
        `*Store` という約束はこの文章にあるだけで、機械的には弾かれません。
    """.trimIndent()
    allowedContents = """
        置いてよいのは取得と保存、そして取得元の詳細（接続、パス、シリアライズ）です。
        名前は `*Store` で揃え、`store` パッケージ直下に置きます。
    """.trimIndent()
    forbiddenContents = """
        - 出力。`println` はエントリポイントの仕事です。保管庫が表示まで持つと、
          テストで差し替える先が無くなります
        - 値の定義。`Note` はモデルの役割です
    """.trimIndent()
    example("NoteStore", "メモリ上のノート一覧")
    layout {
        ":".module {
            mainSourceSet / kotlin / modulePackage / "store" / "*".ktFile()
        }
    }
}
