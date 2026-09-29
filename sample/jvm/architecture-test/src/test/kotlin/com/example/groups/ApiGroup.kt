@file:OptIn(ExperimentalKatachiApi::class)

package com.example.groups

import com.example.forbiddenContents
import com.example.roles.controller
import com.example.roles.ktorPlugin
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.DocumentSection
import me.tbsten.katachi.dsl.documentSection

/**
 * A heading this project declared for itself: how the thing on this page is tested.
 *
 * katachi writes the sections it knows how to assemble -- the placement table, the named
 * constraints, the examples -- and stops there, so a heading a team wants in addition is
 * declared once as a `val` and written through the property below. The body is plain Markdown
 * and is placed exactly as written; there is no typed item, so a list is written as a list.
 * A heading below this one would be `TestPolicy.documentSection("...")` rather than a second
 * top level section.
 */
val TestPolicy: DocumentSection = documentSection("Test policy")

/** Writes the body of [TestPolicy], on a group or on the root of the definition. */
var DeclarationContainerScope.testPolicy: String? by TestPolicy

/** Roles of the API layer: everything that faces HTTP. */
fun DeclarationContainerScope.apiGroup() = "api".group {
    title = "API"
    summary = "The layer that faces HTTP. It owns everything from receiving a request to returning a response"

    description = """
        The layer that an incoming request hits first. It is also a wall that keeps the rest of
        the application from having to know about `io.ktor.server.*`.

        It gathers two things. A controller is the entry point for one endpoint; the Ktor plugin
        configuration holds the settings that apply once to the whole Application, plus the wiring
        that decides which Controllers are connected to the routing tree. Both are about "how HTTP
        is received" and change for the same reasons, so they share a group.

        Note that katachi's `layout { }` only looks at where files live and what they are named.
        A convention such as "a Controller does not call a Repository directly" is only written
        here as prose; nothing rejects it mechanically.
    """.trimIndent()

    forbiddenContents = """
        What must not be placed here is the decision of "what to return" and where a value comes
        from. The former belongs to the domain, the latter to the data layer. In fact,
        `io.ktor.server.*` is imported only in this layer and the entry point, and never in
        `service`, `repository` or `model`.
    """.trimIndent()

    testPolicy = """
        Test the following.

        - That the routing is registered: start a `testApplication` and send real requests
        - That the status code and the required keys in the JSON body are correct
        - That a path that was not registered returns 404

        A Service can be swapped through its constructor, so branch coverage belongs to the
        domain tests; here we only look at the shape that comes out over HTTP.
    """.trimIndent()

    controller()
    ktorPlugin()
}
