package me.tbsten.katachi.dsl

import kotlin.reflect.KProperty

/**
 * Receiver of `template { }`: the parameters a generated file is filled in from, and the files
 * it produces.
 *
 * A template is written where the role is declared, because the role already says where its
 * files may live. `file(...)` therefore names a **file**, never a path: the directory it lands
 * in is read back out of that role's `layout { }`, which is the one place that knows it.
 *
 * The block is stored, not evaluated, exactly like [RoleScope.layout] — `architecture { }` runs
 * long before anyone passes `--arg`. It is replayed once per run, with the values of that run
 * bound, so a parameter read anywhere inside it is an ordinary `String` and Kotlin's own string
 * templates are the whole templating language.
 *
 * ## Example 1: declare a template on a role
 * ```kt
 * val arch = architecture {
 *     "domain".group {
 *         "UseCase" {
 *             layout { "useCase" / "*UseCase.kt".file() }
 *             template {
 *                 val name by stringParameter()
 *                 val implBody by stringParameter(default = """TODO("not implemented")""")
 *
 *                 file("${name}UseCase.kt") {
 *                     """
 *                     interface ${name}UseCase {
 *                         suspend operator fun invoke()
 *                     }
 *
 *                     class ${name}UseCaseImpl : ${name}UseCase {
 *                         override suspend fun invoke() {
 *                             $implBody
 *                         }
 *                     }
 *                     """.trimIndent()
 *                 }
 *             }
 *         }
 *     }
 * }
 * arch.allRoles.single().name shouldBe "UseCase"
 * ```
 *
 * ## Example 2: keep the template out of the definition, in an extension function
 * ```kt
 * private fun RoleScope.useCaseTemplate() = template {
 *     val name by stringParameter()
 *     file("${name}UseCase.kt") { "interface ${name}UseCase" }
 * }
 *
 * val arch = architecture {
 *     "domain".group {
 *         "UseCase" {
 *             layout { "useCase" / "*UseCase.kt".file() }
 *             useCaseTemplate()
 *         }
 *     }
 * }
 * arch.allRoles.single().name shouldBe "UseCase"
 * ```
 *
 * @see RoleScope.template
 * @see TemplateParameter
 */
@KatachiDsl
public sealed interface TemplateScope {
    /**
     * Declares a parameter whose value arrives as `--arg <name>=<value>`.
     *
     * **The property it is written through is its name.** `val name by stringParameter()`
     * declares the parameter `name`, so the word is spelled once and the command line and the
     * template cannot drift apart. Always write it with `by`: a [TemplateParameter] that never
     * reaches a property has no name, and is refused when the block ends.
     *
     * A parameter with no [default] must be given a value on every run. A [default] is an
     * ordinary Kotlin expression evaluated where it is written, so it may read the parameters
     * declared above it.
     *
     * ## Example 1: declare a required parameter and one with a default
     * ```kt
     * template {
     *     val name by stringParameter()
     *     val implBody by stringParameter(default = """TODO("not implemented")""")
     *
     *     // ./gradlew runKatachiProcessor --processor=template --arg roleName=UseCase \
     *     //   --arg name=GetUser
     *     file("${name}UseCase.kt") { "// $implBody" }
     * }
     * ```
     */
    public fun stringParameter(default: String? = null): TemplateParameter

    /**
     * Declares one file this template produces, and how to fill it in.
     *
     * [name] is a file name with its extension, such as `"${name}UseCase.kt"` — not a path.
     * A separator in it is refused: the directory is derived from the role's `layout { }`, and
     * spelling it here would be the same thing said twice, in two places that can disagree.
     *
     * ## Example 1: produce two files whose names are built from one parameter
     * ```kt
     * template {
     *     val name by stringParameter()
     *
     *     file("${name}UseCase.kt") { "interface ${name}UseCase" }
     *     file("${name}UseCaseImpl.kt") { "class ${name}UseCaseImpl : ${name}UseCase" }
     * }
     * ```
     *
     * @throws KatachiInvalidTemplateFileNameException when [name] is blank or is not a plain
     *   file name.
     * @throws KatachiDuplicateTemplateFileException when this template already produces a file
     *   of that name.
     */
    public fun file(name: String, content: () -> String)
}

/**
 * A parameter of a [TemplateScope], bound to the property it is written through.
 *
 * Read it and an ordinary `String` comes back, so a template is written in Kotlin's own string
 * templates and nothing else. The value is whatever `--arg <name>=<value>` carried for the run
 * being replayed, or the default it was declared with.
 *
 * ## Example 1: declare two parameters, one of them with a default
 * ```kt
 * template {
 *     val name by stringParameter()
 *     val implBody by stringParameter(default = """TODO("not implemented")""")
 *
 *     file("${name}UseCase.kt") { "// $implBody" }
 * }
 * ```
 *
 * @see TemplateScope.stringParameter
 */
public class TemplateParameter internal constructor(
    private val binder: TemplateParameterBinder,
    internal val default: String?,
    internal val declaredAt: DeclarationSite,
) {
    /** The property this was written through, or `null` while it has not reached one yet. */
    internal var name: String? = null
        private set

    /**
     * Takes the property's name as the parameter's name, before the property exists.
     *
     * This is what makes `val name by stringParameter()` declare the parameter `name` even when
     * nothing ever reads it — the name is needed to tell a caller which `--arg` this template
     * accepts, and that question is asked before a single file is rendered.
     *
     * ## Example 1: a parameter nothing reads is still one this template accepts
     * ```kt
     * template {
     *     @Suppress("UNUSED_VARIABLE")
     *     val packageName by stringParameter(default = "com.example")
     *     val name by stringParameter()
     *
     *     file("${name}UseCase.kt") { "interface ${name}UseCase" }
     * }
     * ```
     */
    public operator fun provideDelegate(thisRef: Any?, property: KProperty<*>): TemplateParameter {
        bindTo(property.name)
        return this
    }

    /**
     * The value bound for this run.
     *
     * Also binds the name, for the same reason [provideDelegate] does: whichever of the two the
     * compiler reaches first is the one that names the parameter.
     *
     * ## Example 1: read a parameter as the `String` it stands for
     * ```kt
     * template {
     *     val name by stringParameter()
     *
     *     // `name` is an ordinary String here, so Kotlin's own string templates are the whole
     *     // templating language.
     *     file("${name}UseCase.kt") { "interface ${name}UseCase" }
     * }
     * ```
     */
    public operator fun getValue(thisRef: Any?, property: KProperty<*>): String {
        bindTo(property.name)
        return binder.valueOf(this)
    }

    private fun bindTo(propertyName: String) {
        val current = name
        if (current == propertyName) return
        if (current != null) {
            throw KatachiTemplateParameterReusedException(
                firstName = current,
                secondName = propertyName,
                declaredAt = declaredAt,
            )
        }
        name = propertyName
        binder.bind(this)
    }

    // Reached only when a parameter is interpolated instead of the property it was written
    // through, which is the `val name = stringParameter()` mistake. Saying where it was declared
    // is what makes the file name that came out of it explainable.
    override fun toString(): String =
        name?.let { "TemplateParameter($it)" } ?: "TemplateParameter(unbound, $declaredAt)"
}

/** What a [TemplateParameter] talks back to: the scope that is collecting this replay. */
internal interface TemplateParameterBinder {
    /** Takes the name the parameter has just been given. */
    fun bind(parameter: TemplateParameter)

    /** The value bound for this run, the declared default, or a stand-in noted as missing. */
    fun valueOf(parameter: TemplateParameter): String
}
