package com.example.groups

import com.example.forbiddenContents
import com.example.roles.repository
import me.tbsten.katachi.dsl.DeclarationContainerScope

/** Roles of the data layer: where the values come from. */
fun DeclarationContainerScope.dataGroup() = "data".group {
    title = "データ"
    summary = "値がどこから来るのかを引き受ける層"

    description = """
        取得元との往復をここに閉じ込める層です。いまはリポジトリの役割1つだけですが、
        グループを立てているのは、キャッシュや外部 API クライアントのように「取得元が変わったら
        直す」ものが増える先を、あらかじめ決めておくためです。

        Repository が返すのはドメインのモデルで、取得元に固有の型をこの層の外へ出しません。
        Service は `HealthRepository.load()` を呼ぶだけで、その先が DB なのか固定値なのかを
        知らずに済みます。
    """.trimIndent()

    forbiddenContents = """
        ここに置いてはいけないのは、アプリ固有の判断です。何を優先するか・どう組み合わせるかは
        ドメインの担当で、この層は言われたものを取ってくるところまでにします。
    """.trimIndent()

    repository()
}
