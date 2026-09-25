@file:OptIn(ExperimentalKatachiApi::class)

package com.example.groups

import com.example.roles.controller
import com.example.roles.ktorPlugin
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.DocumentSection
import me.tbsten.katachi.dsl.documentSection

/**
 * A heading this project declared for itself: how the thing on this page is tested.
 *
 * katachi writes the sections it knows how to assemble -- the placement table, the named
 * constraints, the examples -- and stops there, so a heading a team wants in addition is
 * declared once as a `val` and written through the property below. The body is plain Markdown
 * and is placed exactly as written; there is no typed item, so a list is written as a list.
 * A heading below this one would be `TestPolicy.documentSection("...")` rather than a second
 * top level section.
 */
val TestPolicy: DocumentSection = documentSection("テスト方針")

/** Writes the body of [TestPolicy], on a group or on the root of the definition. */
var DeclarationContainerScope.testPolicy: String? by TestPolicy

/** Roles of the API layer: everything that faces HTTP. */
fun DeclarationContainerScope.apiGroup() = "api".group {
    title = "API"
    summary = "HTTP に面する層。リクエストを受け取り、応答を返すところまでを持つ"

    description = """
        外から来たリクエストが最初に当たる層です。アプリの中身が `io.ktor.server.*` を
        知らずに済むようにするための壁でもあります。

        集めているのは2つです。コントローラは1エンドポイントぶんの受け口、Ktor プラグイン設定は
        Application 全体に一度だけ効く設定と、どの Controller を routing ツリーに繋ぐかの配線です。
        どちらも「HTTP をどう受けるか」の話で、変えたくなる理由が同じなので同じグループに
        しています。

        ここに置いてはいけないのは「何を返すか」の判断と、値がどこから来るかです。前者は
        ドメイン、後者はデータの担当になります。実際、`io.ktor.server.*` の import が出てくるのは
        この層とエントリポイントだけで、`service` `repository` `model` には1つもありません。

        ただし katachi の `layout { }` が見ているのは置き場所とファイル名までです。
        「Controller から Repository を直接呼ばない」といった約束は、ここに文章として
        書いてあるだけで、機械的には弾かれません。
    """.trimIndent()

    testPolicy = """
        以下をテストする。

        - ルーティングが登録されていること。`testApplication` を立てて実際にリクエストを投げる
        - 応答のステータスコードと、本文の JSON に必要なキーが入っていること
        - 登録していないパスが 404 になること

        Service はコンストラクタで差し替えられるようにしてあるので、分岐の網羅はドメイン側の
        テストで行い、ここでは HTTP に出てくる形だけを見る。
    """.trimIndent()

    controller()
    ktorPlugin()
}
