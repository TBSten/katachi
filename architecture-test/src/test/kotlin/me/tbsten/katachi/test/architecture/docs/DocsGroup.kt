package me.tbsten.katachi.test.architecture.docs

import me.tbsten.katachi.dsl.DeclarationContainerScope

fun DeclarationContainerScope.docsGroup() = "docs".group {
    title = "ドキュメント"

    docsSite()
    docsScript()
    internalGuide()
    projectDocument()
    apiDocsModule()
}
