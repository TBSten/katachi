package com.example.roles

import com.example.modulePackage
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/** The role that faces HTTP: one request in, one service call, one response out. */
fun DeclarationContainerScope.controller() = "Controller" {
    title = "コントローラ"
    summary = "HTTP のリクエストを1つ受け取り、対応する Service を呼んで結果を返す"
    description = """
        HTTP とアプリケーションの中身との境目です。パスとメソッドの登録、リクエストからの値の
        取り出し、Service が返した値を応答にするところまでを持ちます。

        置いてよいのは Ktor の `Route` に対する登録と、受け渡しのための変換だけです。
        `HealthController` は `register(route: Route)` の中で `route.get("/health") { ... }` を
        書き、呼び出す `HealthService` は既定値付きのコンストラクタ引数で受け取るので、
        テストから差し替えられます。

        置いてはいけないもの:

        - 条件分岐や計算。「どちらを返すか」を決めた時点で、それはサービスの仕事です
        - `com.example.repository` の呼び出し。Controller から直接データを取りに行きません
        - `install(...)` のような Application 全体への設定と `routing { }` 自体。どの Controller を
          routing ツリーに繋ぐかは `plugin/Routing.kt` が決めます

        1ファイル1コントローラで、ファイル名は `*Controller.kt`。ファイル名がそのまま
        エンドポイントのまとまりを表すので、新しいパスを既存のファイルに足すのか新しく作るのかを
        名前だけで判断できます。なお `layout { }` が見ているのは置き場所と名前までで、
        上の「置いてはいけないもの」を機械的に弾いてはいません。
    """.trimIndent()
    example("HealthController", "ヘルスチェックの受け口")
    layout {
        // The application is the root project, so its module path is `":"`. What the
        // chain says is the same tree step 2 spelled out by hand: `mainSourceSet` is
        // `src/main`, `kotlin` is the directory of that name, and `modulePackage`
        // derives `com/example` from the module being evaluated.
        ":".module {
            mainSourceSet / kotlin / modulePackage / "controller" / "*Controller".ktFile()
        }
    }
}
