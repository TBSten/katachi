@file:OptIn(ExperimentalKatachiApi::class)

package com.example.api

import com.example.api.controller
import com.example.api.ktorPlugin
import com.example.forbiddenContents
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.DocumentSection
import me.tbsten.katachi.dsl.documentSection

val TestPolicy: DocumentSection = documentSection("Test policy")

var DeclarationContainerScope.testPolicy: String? by TestPolicy

fun DeclarationContainerScope.apiGroup() = "Api".group {
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
        What a file of this layer may import and declare is written in each role's
        `ktFileContent { }`, so "a Controller does not call a Repository directly" fails the test
        as soon as a Controller imports one. What a function does in its body is only written
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
    // A Controller and the Service it calls, as one set. The members come from two groups
    // (`Api` and `Domain`), each named by its own path, and each gets its own value by an
    // expression: the Controller takes the resource as it is, the Service takes it capitalised.
    //   ./gradlew :architecture-test:katachiTemplate --arg template=Api.Endpoint --arg resource=greeting
    // writes `controller/greeting/GreetingController.kt` and `service/GreetingService.kt`.
    "Endpoint".templateSet {
        title = "Endpoint"
        summary = "A Controller and the Service it calls"
        val resource by stringParameter()

        template("Api.Controller") {
            arg("resource", resource)
        }
        template("Domain.Service") {
            arg("name", resource.replaceFirstChar(Char::uppercaseChar))
        }
    }
}
