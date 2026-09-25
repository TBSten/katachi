package com.example.roles

import com.example.modulePackage
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.LayoutScope
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.konsist.konsist

/** The role holding one application-specific behaviour, built out of repositories. */
fun DeclarationContainerScope.service() = "Service" {
    title = "サービス"
    summary = "アプリ固有の振る舞いを1つ持ち、Repository を組み合わせて実現する"
    description = """
        このアプリが何をするのかを書く場所です。Controller から呼ばれ、必要な Repository を
        組み合わせて、モデルを返します。`HealthService.currentHealth()` はいまのところ
        `HealthRepository.load()` の結果を返すだけですが、判断が増えたときに増える先はここです。

        置いてよいのは、アプリ固有の手順・判断・組み立てです。複数の Repository をまたぐ処理や、
        取得した値を突き合わせる処理はここに来ます。

        置いてはいけないもの:

        - `Route`・`call`・`respond` といった Ktor の型。HTTP を知るのは API 層までです
        - 取得元の詳細（接続先・クエリ・ファイルパス）。それはリポジトリが隠します
        - 値の定義そのもの。data class はモデルの役割です

        ファイル名は `*Service.kt` で、1ファイル1クラス。この役割だけが `konsist { }` で
        「public であること」を制約として書いており、うっかり `internal` を付けるとテストが
        落ちます。別パッケージの Controller から参照できなくなる前に気づけます。
    """.trimIndent()
    example("HealthService", "サーバ稼働状態の取得")
    layout {
        ":".module {
            mustBePublic()
            mainSourceSet / kotlin / modulePackage / "service" / "*Service".ktFile()
        }
    }
}

// Kept in this file rather than in a shared one: the declaration site is the first frame
// outside katachi, so a violation keeps naming the role that owns the rule.
//
// Adoption step 4 of katachi's own README (`konsist-integration`): the one line that shows a
// backend-written constraint next to katachi's own layout vocabulary. Also stands as the
// regression test for `LayoutNode.synthetic` — a `":".module { }` block injects `build` and
// `build.gradle.kts`, and this constraint would wrongly cover `build.gradle.kts` if that
// exclusion ever broke.
private fun LayoutScope.mustBePublic() =
    "public であること".konsist {
        classes().must { it.hasPublicOrDefaultModifier }
    }
