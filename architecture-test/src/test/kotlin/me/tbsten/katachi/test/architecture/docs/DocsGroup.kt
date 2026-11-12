package me.tbsten.katachi.test.architecture.docs

import me.tbsten.katachi.dsl.DeclarationContainerScope

fun DeclarationContainerScope.docsGroup() = "Docs".group {
    title = "ドキュメント"

    docsSite()
    docsScript()
    internalGuide()
    projectDocument()
    apiDocsModule()
}
