package me.tbsten.katachi.test.architecture.docs

import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.test.architecture.docs.docsite.docSiteGroup

fun DeclarationContainerScope.docsGroup() = "Docs".group {
    title = "ドキュメント"

    docSiteGroup()
    internalDocs()
    projectDocument()
    samples()
}
