package com.example.sample.roles

import com.example.sample.forbiddenContents
import me.tbsten.katachi.dsl.DeclarationContainerScope

/**
 * The role of the baseline file that `baseline()` in `ProjectArchitecture.kt`
 * names.
 *
 * The file is a file of the project like any other, so `files = gitTracked()` hands it to the
 * check and it needs a role; without one the test fails with `[UnexpectedFile]` about it.
 */
fun DeclarationContainerScope.baselineFile() = "BaselineFile" {
    title = "baseline（棚上げした違反の台帳）"
    summary = "katachi を入れた時点ですでにあった違反を記録し、テストを落とさずに棚上げしておく台帳"
    description = """
        `ProjectArchitecture.kt` の `baseline()` が指すファイル。ここに記録した
        違反は `:architecture-test:test` を落とさず、「held back N violations」と件数だけが出る。
        記録に無い新しい違反は、これまでどおりテストを落とす。

        このサンプルでは、baseline の見本として2件を意図的に残してある。
        `:feature:home` の `HomeFormatter.kt`（feature package の直下に置けるのは
        Route / Screen / ViewModel だけなので `[UnexpectedFile]`）と、`:data` の `legacy/`
        （`user` と `settings` 以外の package なので、ディレクトリごと `[UnexpectedDirectory]`）。

        手では書かない。更新は次の2つで行う。

        - `./gradlew :architecture-test:test -Dkatachi.baseline.update=true` — 今ある違反で丸ごと作り直す
        - `./gradlew :architecture-test:test -Dkatachi.baseline.prune=true` — 直した違反の項目だけを消す

        違反を直すと、その項目は「もう無い違反を棚上げしている」として `[StaleBaselineEntry]` で
        テストを落とす。prune で項目を消すまで落ち続けるので、棚上げの件数は減る一方になる。
        CI（環境変数 `CI=true`）では update も prune も拒否され、突き合わせだけを行う。
    """.trimIndent()
    forbiddenContents = """
        - 恒久的に認めたいもの。それは台帳ではなく、定義の `layout { }` に役割として書く
        - 手で足した項目。次の update で書き戻される
    """.trimIndent()
    example("katachi-baseline.json", "棚上げした違反の一覧")
    layout {
        "katachi-baseline.json".file()
    }
}
