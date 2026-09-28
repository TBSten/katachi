package com.example.roles

import com.example.allowedContents
import com.example.forbiddenContents
import com.example.modulePackage
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/**
 * The role that faces HTTP: one request in, one service call, one response out.
 *
 * Each controller sits in a package of its own resource, `controller/health/HealthController.kt`,
 * and that level is `capture("resource")` rather than `"*"`. The check reads the two the same
 * way; the name is what lets the template below be told where to generate:
 * `--arg resource=user --arg name=User` writes `controller/user/UserController.kt`, and a
 * resource that has no package yet gets a new one.
 */
fun DeclarationContainerScope.controller() = "Controller" {
    title = "コントローラ"
    summary = "HTTP のリクエストを1つ受け取り、対応する Service を呼んで結果を返す"
    description = """
        HTTP とアプリケーションの中身との境目です。パスとメソッドの登録、リクエストからの値の
        取り出し、Service が返した値を応答にするところまでを持ちます。

        1ファイル1コントローラで、ファイル名は `*Controller.kt`。リソースごとに
        `controller/health/` のような package を1つ切り、その中に置きます。ファイル名がそのまま
        エンドポイントのまとまりを表すので、新しいパスを既存のファイルに足すのか新しく作るのかを
        名前だけで判断できます。なお `layout { }` が見ているのは置き場所と名前までで、
        「置いてはいけないもの」を機械的に弾いてはいません。

        テンプレートから生成できます。リソースの package の階層に `resource` と名前を付けてあるので、
        `--arg resource=user --arg name=User` で `controller/user/UserController.kt` ができます。
    """.trimIndent()
    allowedContents = """
        置いてよいのは Ktor の `Route` に対する登録と、受け渡しのための変換だけです。
        `HealthController` は `register(route: Route)` の中で `route.get("/health") { ... }` を
        書き、呼び出す `HealthService` は既定値付きのコンストラクタ引数で受け取るので、
        テストから差し替えられます。
    """.trimIndent()
    forbiddenContents = """
        - 条件分岐や計算。「どちらを返すか」を決めた時点で、それはサービスの仕事です
        - `com.example.repository` の呼び出し。Controller から直接データを取りに行きません
        - `install(...)` のような Application 全体への設定と `routing { }` 自体。どの Controller を
          routing ツリーに繋ぐかは `plugin/Routing.kt` が決めます
    """.trimIndent()
    example("HealthController", "ヘルスチェックの受け口")
    layout {
        // The application is the root project, so its module path is `":"`. What the
        // chain says is the same tree as `src/main/kotlin/com/example/controller` spelled
        // out by hand: `mainSourceSet` is `src/main`, `kotlin` is the directory of that
        // name, and `modulePackage` derives `com/example` from the module being evaluated.
        // `capture("resource")` is a `*` with a name: one package level per resource.
        ":".module {
            mainSourceSet / kotlin / modulePackage / "controller" / capture("resource") /
                "*Controller".ktFile()
        }
    }
    // The directory comes from the layout: `--arg resource=...` fills in `capture("resource")`.
    //   ./gradlew :architecture-test:katachiTemplate \
    //       --arg roleName=Controller --arg resource=user --arg name=User
    template {
        val resource = captureValue("resource")
        val name by stringParameter()

        file("${name}Controller.kt") {
            """
                package com.example.controller.$resource

                import io.ktor.server.response.respondText
                import io.ktor.server.routing.Route
                import io.ktor.server.routing.get

                /** Maps `GET /$resource`. */
                class ${name}Controller {
                    fun register(route: Route) {
                        route.get("/$resource") {
                            call.respondText("TODO: ${name}Controller")
                        }
                    }
                }
            """.trimIndent() + "\n"
        }
    }
}
