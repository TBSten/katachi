package me.tbsten.katachi.test.architecture.groups

import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.test.architecture.roles.apiDocsModule
import me.tbsten.katachi.test.architecture.roles.docsSite
import me.tbsten.katachi.test.architecture.roles.internalGuide
import me.tbsten.katachi.test.architecture.roles.projectDocument

/**
 * The roles of the documentation.
 *
 * `docs/` is an Astro site plus the internal guides the agents and the maintainers read. Two
 * roles for those, because the two halves are owned by different things: Astro's conventions
 * decide the shape of the site, and this repository decides the shape of the guides.
 *
 * `ProjectDocument` and `ApiDocsModule` join them from outside `docs/` — a group is a set of
 * roles that belong together, not a directory. See `ProjectDocument`'s own file for why README
 * and LICENSE are documentation rather than tooling; `ApiDocsModule` is the generated API
 * reference's `Module.md`, one per published module, and its own file says why it exists at all.
 */
fun DeclarationContainerScope.docsGroup() = "docs".group {
    title = "ドキュメント"

    docsSite()
    internalGuide()
    projectDocument()
    apiDocsModule()
}
