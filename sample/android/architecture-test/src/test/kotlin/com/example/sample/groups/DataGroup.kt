package com.example.sample.groups

import com.example.sample.roles.repository
import me.tbsten.katachi.dsl.DeclarationContainerScope

/** Roles of the data layer: what `:data` holds. */
fun DeclarationContainerScope.dataGroup() = "data".group {
    title = "データレイヤー"
    summary = ":data が持つもの。データの取得と保存"
    description = """
        `:data` モジュール。いまは Repository 役割1つだけで、扱う対象ごとの package
        （`user` / `settings`）にインターフェースと実装を並べる。

        他のどのモジュールにも依存しない、このアプリで一番下の層。Android にも Compose にも
        触らないので、`:ui` や `:feature:*` を持ち出さずに読める。ViewModel はここの
        インターフェースをコンストラクタで受け取り、テストでは `:testing` のフェイクに差し替える。

        役割が1つしか無くても group にしてあるのは、増える場所だから。データソースや DTO を
        分けたくなったらここに足す。いまは Repository 以外のファイルを `:data` に置くと違反になるので、
        「置いてから考える」ができないようになっている。
    """.trimIndent()

    repository()
}
