package me.tbsten.katachi.test.architecture.roles

import me.tbsten.katachi.dsl.DeclarationContainerScope

/** The role of the written-out declarations each sample keeps, so a change is reviewed as a diff. */
fun DeclarationContainerScope.layoutSnapshot() = "LayoutSnapshot" {
    title = "レイアウトのスナップショット"
    summary = "各サンプルの宣言を全文書き出したもの。定義の変化を人がレビューするために差分で見せる"
    example("jvm.txt", "sample/jvm の宣言の全文")
    layout {
        "sample" / "layout-snapshots" / "*.txt".file()
    }
}
