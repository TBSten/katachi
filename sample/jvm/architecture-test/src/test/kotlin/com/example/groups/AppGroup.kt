package com.example.groups

import com.example.roles.entrypoint
import com.example.roles.serverConfig
import me.tbsten.katachi.dsl.DeclarationContainerScope

/** Roles that assemble and configure the running process. */
fun DeclarationContainerScope.appGroup() = "app".group {
    title = "アプリケーション"
    summary = "プロセスの起動と、実行時に読み込まれる設定"

    description = """
        「このプロセスはどう始まるのか」に答えるものを集めた層です。レイヤーというより、
        他のどの層にも属さない起動まわりの置き場所です。

        入っているのはエントリポイント（`Application.kt`）とサーバ設定（`application.conf` と
        `logback.xml`）です。この2つは対になっていて、`application.conf` の `modules` が
        `com.example.ApplicationKt.module` を名指しし、その関数が各プラグイン設定を呼びます。
        片方だけ直すと起動しないので、同じ場所で読めるようにしてあります。

        ここに置かないのは、ビルド時にしか効かない設定（Gradle スクリプトの役割）と、
        個々の `install(...)` の中身（API 層の Ktor プラグイン設定の役割）です。
    """.trimIndent()

    entrypoint()
    serverConfig()
}
