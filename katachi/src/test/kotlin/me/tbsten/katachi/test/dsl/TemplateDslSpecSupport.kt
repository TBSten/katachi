package me.tbsten.katachi.test.dsl

import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.TemplateScope
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.internal.LayoutTemplate
import me.tbsten.katachi.dsl.internal.Template
import me.tbsten.katachi.dsl.internal.TemplateParameterNames
import me.tbsten.katachi.dsl.internal.evaluateTemplate
import me.tbsten.katachi.dsl.internal.flattenLayout
import me.tbsten.katachi.dsl.internal.templateParameterNames
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.dsl.template

/** The role every template of the dsl specs is declared on, so the messages have a name to print. */
internal const val TEMPLATE_ROLE: String = "UseCase"

internal fun architectureWithTemplate(block: TemplateScope.() -> String): Architecture =
    architecture {
        "domain".group { TEMPLATE_ROLE { layout { "Generated".ktFile().template(block = block) } } }
    }

/** The one `.template { }` this file's `architectureWithTemplate` declared. */
private fun Architecture.singleTemplate(): LayoutTemplate =
    flattenLayout().mapNotNull { it[Template] }.single()

/** The rendered content of the one template. */
internal fun Architecture.render(values: Map<String, String> = emptyMap()): String =
    evaluateTemplate(singleTemplate(), TEMPLATE_ROLE, values)

/** What a names-only replay of the one template answers for a run given [values]. */
internal fun Architecture.namesReplay(values: Map<String, String> = emptyMap()): TemplateParameterNames =
    templateParameterNames(singleTemplate(), TEMPLATE_ROLE, values)

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
