package me.tbsten.katachi.test.dsl

import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.TemplateScope
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.internal.TemplateParameterNames
import me.tbsten.katachi.dsl.internal.evaluateTemplate
import me.tbsten.katachi.dsl.internal.templateParameterNames

/** The role every template of the dsl specs is declared on, so the messages have a name to print. */
internal const val TEMPLATE_ROLE: String = "UseCase"

internal fun architectureWithTemplate(block: TemplateScope.() -> Unit): Architecture =
    architecture {
        "domain".group { TEMPLATE_ROLE { template(block) } }
    }

/** The rendered files of one template, as `file name -> content`. */
internal fun Architecture.render(values: Map<String, String> = emptyMap()): Map<String, String> =
    evaluateTemplate(allRoles.single().templates.single(), TEMPLATE_ROLE, values)
        .files
        .associate { it.fileName to it.content }

/** What a names-only replay of the one template answers for a run given [values]. */
internal fun Architecture.namesReplay(values: Map<String, String> = emptyMap()): TemplateParameterNames =
    templateParameterNames(allRoles.single().templates.single(), TEMPLATE_ROLE, values)

internal fun Architecture.parameterNames(values: Map<String, String> = emptyMap()): Set<String> =
    namesReplay(values).declared

/** The enum of `TemplateScope`'s KDoc. An enum class cannot be local, so the specs share these. */
enum class Visibility { Public, Internal }

/** An enum whose entry has a body, which makes that entry a subclass of its own. */
enum class Shape {
    Circle {
        override fun toString(): String = "a circle"
    },
    Square,
}

/** An enum no value can ever be read as. */
enum class Nothingness
