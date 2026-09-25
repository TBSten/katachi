package com.example.roles

import com.example.modulePackage
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile

/** The role that owns where a value comes from, so the domain does not have to know. */
fun DeclarationContainerScope.repository() = "Repository" {
    title = "リポジトリ"
    summary = "データの取得・保存を担い、取得元の詳細をドメインから隠す"
    description = """
        値がどこから来るのかを引き受ける場所です。Service は `HealthRepository.load()` を呼ぶだけで、
        その先が DB なのかファイルなのか固定値なのかを知りません。取得元が変わったときに直す範囲を
        この役割の中に閉じ込めるのが目的です。

        置いてよいのは、外部の取得元との往復と、その結果をモデルに詰め替えるところまでです。
        このサンプルの `HealthRepository` は DB を持たず `Health(status = "UP", version = "0.1.0")` を
        返すだけですが、katachi が見ている「どこに置かれ、何という名前か」の点では本物と同じ形を
        しています。

        置いてはいけないもの:

        - アプリ固有の判断。何を優先するか・どう組み合わせるかはサービスの役割です
        - Ktor の型。HTTP のリクエストはここまで降りてきません
        - 取得元に固有の型を外に返すこと。戻り値はモデルに揃えます

        ファイル名は `*Repository.kt` で、1ファイル1クラスです。
    """.trimIndent()
    example("HealthRepository", "稼働状態の取得元")
    layout {
        ":".module {
            mainSourceSet / kotlin / modulePackage / "repository" / "*Repository".ktFile()
        }
    }
}
