package me.tbsten.katachi.test.architecture.docs.docsite

import me.tbsten.katachi.dsl.DeclarationContainerScope

fun DeclarationContainerScope.docSiteGroup() = "DocSite".group {
    title = "ドキュメントサイト"

    site()
    dokka()
    dokkaPlugin()
    docsScript()
    apiDocsModule()
}
