package me.tbsten.katachi.test.architecture.docs

import me.tbsten.katachi.dsl.DeclarationContainerScope

fun DeclarationContainerScope.samples() = "Samples".role {
    title = "サンプル"
    summary = "利用者向けのサンプルプロジェクト。独立したビルドなので、ここでは中を見ない"
    layout {
        "sample".ignore()
    }
}
