package me.tbsten.katachi.dsl

import kotlin.enums.EnumEntries
import kotlin.reflect.KProperty
import me.tbsten.katachi.dsl.internal.TemplateParameterBinder
import me.tbsten.katachi.dsl.internal.TemplateParameterType

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
 * bound, so a parameter read anywhere inside it is an ordinary value of the type it was declared
 * with, and Kotlin's own string templates are the whole templating language.
 *
 * Every parameter is read as its `--arg <name>=<value>`, whatever its type. A value that does not
 * fit the type, and a value that is missing, are not reported where the parameter is declared:
 * they are collected over the whole replay and reported together once it ends.
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
 * ## Example 3: choose which files to produce, and what goes in them, from typed parameters
 * ```kt
 * enum class Visibility { Public, Internal } // an enum class cannot be local: declare it at the top level
 *
 * val arch = architecture {
 *     "data".group {
 *         "Repository" {
 *             layout {
 *                 "repository" / "*Repository.kt".file()
 *                 "repository" / "*RepositoryImpl.kt".file()
 *             }
 *             template {
 *                 val name by stringParameter()
 *                 val withImpl by booleanParameter(default = true)
 *                 val pageSize by intParameter(default = 20)
 *                 val visibility by enumParameter(default = Visibility.Public)
 *                 val modifier = visibility.name.lowercase()
 *
 *                 // ./gradlew :architecture-test:katachiTemplate \
 *                 //   --arg roleName=Repository --arg name=User \
 *                 //   --arg withImpl=false --arg pageSize=50 --arg visibility=Internal
 *                 file("${name}Repository.kt") {
 *                     "$modifier interface ${name}Repository { val pageSize: Int get() = $pageSize }"
 *                 }
 *                 if (withImpl) {
 *                     file("${name}RepositoryImpl.kt") {
 *                         "$modifier class ${name}RepositoryImpl : ${name}Repository"
 *                     }
 *                 }
 *             }
 *         }
 *     }
 * }
 * arch.allRoles.single().name shouldBe "Repository"
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
     *     // ./gradlew katachiTemplate --arg roleName=UseCase \
     *     //   --arg name=GetUser
     *     file("${name}UseCase.kt") { "// $implBody" }
     * }
     * ```
     */
    public fun stringParameter(default: String? = null): TemplateParameter<String>

    /**
     * Declares a parameter read as a `Boolean`, named by its property like [stringParameter].
     *
     * `true` or `false`, in lower case. Any other word is refused, never read as `false`.
     *
     * ## Example 1: produce a file only when asked to
     * ```kt
     * template {
     *     val name by stringParameter()
     *     val withImpl by booleanParameter(default = true) // --arg withImpl=false
     *
     *     file("${name}Repository.kt") { "interface ${name}Repository" }
     *     if (withImpl) file("${name}RepositoryImpl.kt") { "class ${name}RepositoryImpl" }
     * }
     * ```
     */
    public fun booleanParameter(default: Boolean? = null): TemplateParameter<Boolean>

    /**
     * Declares a parameter read as an `Int`, named by its property like [stringParameter].
     *
     * A whole number in decimal, such as `20` or `-1`, within the range of `Int`.
     *
     * ## Example 1: put a number into the generated code
     * ```kt
     * template {
     *     val name by stringParameter()
     *     val pageSize by intParameter(default = 20) // --arg pageSize=50
     *
     *     file("${name}Pager.kt") { "const val PAGE_SIZE: Int = $pageSize" }
     * }
     * ```
     */
    public fun intParameter(default: Int? = null): TemplateParameter<Int>

    /**
     * Declares a required parameter read as one of [entries], named by its property like
     * [stringParameter].
     *
     * One of the entry names, spelled exactly as declared in Kotlin -- not lower-cased the way
     * `onExisting=skip` is.
     *
     * ## Example 1: require one entry of an enum
     * ```kt
     * enum class Visibility { Public, Internal }
     *
     * template {
     *     val name by stringParameter()
     *     val visibility by enumParameter(Visibility.entries) // --arg visibility=Internal
     *
     *     file("${name}.kt") { "${visibility.name.lowercase()} class $name" }
     * }
     * ```
     *
     * @throws KatachiEmptyEnumTemplateParameterException when the enum has no entries, so no
     *   value could ever be read as it.
     */
    public fun <E : Enum<E>> enumParameter(entries: EnumEntries<E>): TemplateParameter<E>

    /**
     * Declares a parameter read as an entry of [default]'s enum, and [default] when none is given.
     *
     * One of the entry names, spelled exactly as declared in Kotlin -- not lower-cased the way
     * `onExisting=skip` is. The entries are taken from [default]'s own enum class.
     *
     * ## Example 1: pick an entry, with one to fall back on
     * ```kt
     * enum class Visibility { Public, Internal }
     *
     * template {
     *     val name by stringParameter()
     *     val visibility by enumParameter(default = Visibility.Public) // --arg visibility=Internal
     *
     *     file("${name}.kt") { "${visibility.name.lowercase()} class $name" }
     * }
     * ```
     */
    public fun <E : Enum<E>> enumParameter(default: E): TemplateParameter<E>

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
 * Read it and an ordinary value of the type it was declared with comes back, so a template is
 * written in Kotlin's own string templates and nothing else. The value is whatever `--arg <name>=<value>` carried for the run
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
 * @see TemplateScope.booleanParameter
 * @see TemplateScope.intParameter
 * @see TemplateScope.enumParameter
 */
public class TemplateParameter<out T> internal constructor(
    private val binder: TemplateParameterBinder,
    internal val type: TemplateParameterType<T>,
    internal val default: T?,
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
    public operator fun provideDelegate(thisRef: Any?, property: KProperty<*>): TemplateParameter<T> {
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
    public operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
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
                declaredWith = type.declaredWith,
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
