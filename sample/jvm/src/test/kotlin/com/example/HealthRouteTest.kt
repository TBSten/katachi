package com.example

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication

/**
 * Proves the sample is a working Ktor application, not just a directory tree that
 * compiles. A layout check that passes over dead code is not worth much.
 */
class HealthRouteTest : FreeSpec({
    "GET /health が 200 と status=UP を返す" {
        testApplication {
            // `testApplication` ignores the `modules` list in application.conf, so the
            // module has to be installed explicitly. Without this every route is 404.
            application { module() }

            val response = client.get("/health")

            response.status shouldBe HttpStatusCode.OK
            response.bodyAsText() shouldContain "\"status\""
            response.bodyAsText() shouldContain "UP"
        }
    }

    "定義していないパスは 404 を返す" {
        testApplication {
            application { module() }

            client.get("/not-registered").status shouldBe HttpStatusCode.NotFound
        }
    }
})
