package com.example.groups

import com.example.forbiddenContents
import com.example.roles.model
import com.example.roles.service
import me.tbsten.katachi.dsl.DeclarationContainerScope

/** Roles of the domain layer: the behaviour and the values the application is about. */
fun DeclarationContainerScope.domainGroup() = "domain".group {
    title = "ドメイン"
    summary = "アプリ固有の振る舞いと、その対象になる値"

    description = """
        このアプリが何をするのかを書く層です。HTTP のことも、値がどこに保存されているかも
        知りません。

        サービスとモデルを一緒に置いているのは、振る舞いとその対象になる値が同じ理由で変わる
        からです。`Health` に項目が増えれば `HealthService` が返すものも変わります。逆に、
        取得元が変わってもこの層は変わらない、というのがデータ層を分けている理由です。

        モデルは `@Serializable` を持ち、API の応答本文としてもそのまま使われます。
        ドメインのモデルと API のペイロードを分けない、という判断を1つしている場所なので、
        ずれ始めたら API 層に DTO の役割を足す形で戻せます。
    """.trimIndent()

    forbiddenContents = """
        ここに置いてはいけないのは、`Route` や `call` といった Ktor の型と、
        接続先やクエリといった取得元の詳細です。
    """.trimIndent()

    service()
    model()
}
