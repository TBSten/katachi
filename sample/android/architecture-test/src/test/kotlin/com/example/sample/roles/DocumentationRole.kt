package com.example.sample.roles

import me.tbsten.katachi.dsl.DeclarationContainerScope

/** The role of the prose that explains the repository to whoever opens it. */
fun DeclarationContainerScope.documentation() = "Documentation" {
    summary = "README.md など、リポジトリを読む人に向けた説明"
    documented = false
    layout {
        "README.md".file()
    }
}
