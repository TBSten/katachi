package com.example.roles

import com.example.allowedContents
import com.example.forbiddenContents
import com.example.modulePackage
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.LayoutScope
import me.tbsten.katachi.dsl.gradle.div
import me.tbsten.katachi.dsl.gradle.kotlin
import me.tbsten.katachi.dsl.gradle.mainSourceSet
import me.tbsten.katachi.dsl.gradle.module
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.dsl.template
import me.tbsten.katachi.konsist.konsist

/** The role holding one application-specific behaviour, built out of repositories. */
fun DeclarationContainerScope.service() = "Service" {
    title = "サービス"
    summary = "アプリ固有の振る舞いを1つ持ち、Repository を組み合わせて実現する"
    description = """
        このアプリが何をするのかを書く場所です。Controller から呼ばれ、必要な Repository を
        組み合わせて、モデルを返します。`HealthService.currentHealth()` はいまのところ
        `HealthRepository.load()` の結果を返すだけですが、判断が増えたときに増える先はここです。

        ファイル名は `*Service.kt` で、1ファイル1クラス。アプリの役割ではこの役割だけが `konsist { }` で
        「public であること」を制約として書いており、うっかり `internal` を付けるとテストが
        落ちます。別パッケージの Controller から参照できなくなる前に気づけます。
    """.trimIndent()
    allowedContents = """
        置いてよいのは、アプリ固有の手順・判断・組み立てです。複数の Repository をまたぐ処理や、
        取得した値を突き合わせる処理はここに来ます。
    """.trimIndent()
    forbiddenContents = """
        - `Route`・`call`・`respond` といった Ktor の型。HTTP を知るのは API 層までです
        - 取得元の詳細（接続先・クエリ・ファイルパス）。それはリポジトリが隠します
        - 値の定義そのもの。data class はモデルの役割です
    """.trimIndent()
    example("HealthService", "サーバ稼働状態の取得")
    // ./gradlew :architecture-test:katachiTemplate --arg template=domain.Service --arg name=User
    layout {
        ":".module {
            mustBePublic()
            mainSourceSet / kotlin / modulePackage / "service" /
                "${capture("name")}Service".ktFile()
                    .template {
                        val name = captureValue("name")
                        val kdoc by stringParameter(default = "$name に関するアプリ固有の振る舞い。")
                        """
                            package com.example.service

                            /** $kdoc */
                            class ${name}Service {
                                fun execute(): String = TODO("${name}Service の実装")
                            }
                        """.trimIndent() + "\n"
                    }
        }
    }
}

// Kept in this file rather than in a shared one: the declaration site is the first frame
// outside katachi, so a violation keeps naming the role that owns the rule.
//
// The samples' one `konsist { }` constraint on application code (katachi's guide: "Konsist integration"): the one
// line that shows a backend-written constraint next to katachi's own layout vocabulary. Also stands as the
// regression test for `LayoutNode.synthetic` — a `":".module { }` block injects `build` and
// `build.gradle.kts`, and this constraint would wrongly cover `build.gradle.kts` if that
// exclusion ever broke.
private fun LayoutScope.mustBePublic() =
    "public であること".konsist {
        classes().must { it.hasPublicOrDefaultModifier }
    }
