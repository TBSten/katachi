package com.example.groups

import com.example.roles.entrypoint
import com.example.roles.model
import com.example.roles.store
import me.tbsten.katachi.dsl.DeclarationContainerScope

/**
 * Roles of the application itself: the smallest thing the processors can be pointed at.
 *
 * It is one group and not three layers on purpose. This sample's subject is in `testing`, and a
 * layered application here would invite a reader to compare architectures instead of reading
 * processors -- `sample/jvm`, `sample/android` and `sample/kmp` are where that comparison
 * belongs.
 */
fun DeclarationContainerScope.coreGroup() = "core".group {
    title = "本体"
    summary = "ノートを読み出して並べるだけの小さなアプリ。processor が読む対象"

    description = """
        アプリ本体です。ノートを保管庫から読み出して標準出力に並べる、それだけのものです。

        小さいのは意図的です。このサンプルの本題は `testing` グループにある3本の processor で、
        本体はそれらが読む対象を用意するために置いてあります。層を切るなら `sample/jvm` の
        `api` / `domain` / `data` を見てください。

        それでも役割を3つに割ってあるのは、`RoleFileCount` の出力にも `RoleTable` の表にも
        複数行が出てほしいからです。1つしか無いと、processor が何をしたのか出力から読み取れません。

        ここに置いてはいけないのは、定義や processor のコードです。どちらも `:architecture-test`
        にあり、`testing` グループの役割が覆います。
    """.trimIndent()

    entrypoint()
    model()
    store()
}
