package com.example.groups

import com.example.forbiddenContents
import com.example.roles.model
import com.example.roles.service
import me.tbsten.katachi.dsl.DeclarationContainerScope

/** Roles of the domain layer: the behaviour and the values the application is about. */
fun DeclarationContainerScope.domainGroup() = "domain".group {
    title = "Domain"
    summary = "The application-specific behaviour, and the values it acts on"

    description = """
        The layer that says what this application does. It knows nothing about HTTP, nor about
        where values are stored.

        Services and models sit together because the behaviour and the values it acts on change
        for the same reasons. If `Health` gains a field, what `HealthService` returns changes too.
        Conversely, this layer does not change when the data source changes, which is why the data
        layer is separate.

        Models carry `@Serializable` and are used as-is as the API response body. This is a place
        where one decision has been made not to separate domain models from API payloads; if the
        two start to drift apart, it can be undone by adding a DTO role to the API layer.
    """.trimIndent()

    forbiddenContents = """
        What must not be placed here is Ktor types such as `Route` and `call`, and data source
        details such as connection targets and queries.
    """.trimIndent()

    service()
    model()
}
