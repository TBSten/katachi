package com.example.groups

import com.example.forbiddenContents
import com.example.roles.repository
import me.tbsten.katachi.dsl.DeclarationContainerScope

/** Roles of the data layer: where the values come from. */
fun DeclarationContainerScope.dataGroup() = "data".group {
    title = "Data"
    summary = "The layer that takes responsibility for where values come from"

    description = """
        A layer that confines the round trips to a data source. It holds just one role, the
        repository, for now. The group exists to decide in advance where things that "get fixed
        when the data source changes", such as caches and external API clients, will go when they
        appear.

        A Repository returns domain models and never lets a type specific to the data source out
        of this layer. A Service only calls `HealthRepository.load()` and does not need to know
        whether what lies behind it is a DB or a fixed value.
    """.trimIndent()

    forbiddenContents = """
        What must not be placed here is application-specific decisions. What to prioritize and how
        to combine things belong to the domain; this layer stops at fetching what it is asked for.
    """.trimIndent()

    repository()
}
