package me.tbsten.katachi.dsl

import me.tbsten.katachi.dsl.internal.TemplateParameterBinder
import me.tbsten.katachi.dsl.internal.TemplateParameterType
import kotlin.enums.EnumEntries
import kotlin.reflect.KProperty

/**
 * Receiver of `.template(id, title) { -> String }`: the parameters a generated file is filled
 * in from, and the block's return value, which is that file's content.
 *
 * A template is attached to the file declaration itself, because that declaration already says
 * where the file lands: `layout { "useCase" / "${capture("name")}UseCase.kt".file().template { ... } }` needs no
 * `file(...)` call of its own to name the file a second time, the way the old, role-level
 * `template { }` did.
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
 * ## Example 1: declare a template on a file declaration
 * ```kt
 * val arch = architecture {
 *     "domain".group {
 *         "UseCase" {
 *             layout {
 *                 "useCase" / "${capture("name")}UseCase.kt".file()
 *                     .template {
 *                         val name = captureValue("name")
 *                         val implBody by stringParameter(default = """TODO("not implemented")""")
 *                         """
 *                         interface ${name}UseCase {
 *                             suspend operator fun invoke()
 *                         }
 *
 *                         class ${name}UseCaseImpl : ${name}UseCase {
 *                             override suspend fun invoke() {
 *                                 $implBody
 *                             }
 *                         }
 *                         """.trimIndent()
 *                     }
 *             }
 *         }
 *     }
 * }
 * arch.allRoles.single().name shouldBe "UseCase"
 * ```
 *
 * ## Example 2: two file declarations, each with its own id, choosing what goes in them from typed parameters
 * ```kt
 * enum class Visibility { Public, Internal } // an enum class cannot be local: declare it at the top level
 *
 * val arch = architecture {
 *     "data".group {
 *         "Repository" {
 *             layout {
 *                 // ./gradlew :architecture-test:katachiTemplate \
 *                 //   --arg template=data.Repository.repository,data.Repository.repositoryImpl \
 *                 //   --arg name=User --arg pageSize=50 --arg visibility=Internal
 *                 "repository" / "${capture("name")}Repository.kt".file()
 *                     .template(id = "repository") {
 *                         val name = captureValue("name")
 *                         val pageSize by intParameter(default = 20)
 *                         val visibility by enumParameter(default = Visibility.Public)
 *                         "${visibility.name.lowercase()} interface ${name}Repository { val pageSize: Int get() = $pageSize }"
 *                     }
 *                 "repository" / "${capture("name")}RepositoryImpl.kt".file()
 *                     .template(id = "repositoryImpl") {
 *                         val name = captureValue("name")
 *                         val visibility by enumParameter(default = Visibility.Public)
 *                         "${visibility.name.lowercase()} class ${name}RepositoryImpl : ${name}Repository"
 *                     }
 *             }
 *         }
 *     }
 * }
 * arch.allRoles.single().name shouldBe "Repository"
 * ```
 *
 * @see LayoutFile.template
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
     * "useCase" / "${capture("name")}UseCase.kt".file()
     *     .template {
     *         val implBody by stringParameter(default = """TODO("not implemented")""")
     *         val comment by stringParameter()
     *
     *         // ./gradlew katachiTemplate --arg template=UseCase --arg name=GetUser --arg comment=hello
     *         "// $comment: ${captureValue("name")}UseCase { $implBody }"
     *     }
     * ```
     */
    public fun stringParameter(default: String? = null): TemplateParameter<String>

    /**
     * Declares a parameter read as a `Boolean`, named by its property like [stringParameter].
     *
     * `true` or `false`, in lower case. Any other word is refused, never read as `false`.
     *
     * ## Example 1: change the content on a Boolean flag
     * ```kt
     * "repository" / "${capture("name")}Repository.kt".file()
     *     .template {
     *         val name = captureValue("name")
     *         val suspending by booleanParameter(default = true) // --arg suspending=false
     *         val modifier = if (suspending) "suspend " else ""
     *         "interface ${name}Repository { ${modifier}fun all(): List<$name> }"
     *     }
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
     * "${capture("name")}Pager.kt".file()
     *     .template {
     *         val name = captureValue("name")
     *         val pageSize by intParameter(default = 20) // --arg pageSize=50
     *         "const val ${name}_PAGE_SIZE: Int = $pageSize"
     *     }
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
     * "${capture("name")}.kt".file()
     *     .template {
     *         val name = captureValue("name")
     *         val visibility by enumParameter(Visibility.entries) // --arg visibility=Internal
     *         "${visibility.name.lowercase()} class $name"
     *     }
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
     * "${capture("name")}.kt".file()
     *     .template {
     *         val name = captureValue("name")
     *         val visibility by enumParameter(default = Visibility.Public) // --arg visibility=Internal
     *         "${visibility.name.lowercase()} class $name"
     *     }
     * ```
     */
    public fun <E : Enum<E>> enumParameter(default: E): TemplateParameter<E>

    /**
     * Reads the value of this declaration's named wildcard [name]: a `capture("...")` level of
     * the path it sits on, whether part of a directory, a file name, or the module key.
     *
     * It arrives as `--arg <name>=<value>`, the same value that decides the directory the file
     * is generated into, so the package or a class name built from it cannot disagree with where
     * the file lands. It is read only, not declared: the layout already declares it, and a
     * parameter of the same name is refused as a conflict. Read by its name as a string rather than
     * through a property because a capture name may hold a `-`, which a property name cannot.
     *
     * A run that gives no value fails the same way as one whose file's place needs it, with
     * `KatachiMissingTemplateCaptureException`. A preview (`katachiTemplates`) reads it as the
     * string `${name}`. The value is checked as one directory level and nothing more, so a template
     * that puts it into a package or a class name tidies or refuses a `-` itself.
     *
     * ## Example 1: build the package from the module the file is generated into
     * ```kt
     * "Screen" {
     *     layout {
     *         ":feature:${capture("feature")}".module {
     *             "${capture("name")}Screen.kt".file()
     *                 .template {
     *                     val name = captureValue("name")
     *                     val feature = captureValue("feature") // --arg feature=home
     *                     "package com.example.feature.$feature\n\nfun ${name}Screen() {}"
     *                 }
     *         }
     *     }
     * }
     * ```
     *
     * @throws KatachiUnknownTemplateCaptureException when this declaration names no wildcard
     *   [name].
     */
    public fun captureValue(name: String): String

    /**
     * Whether this replay is a preview (`katachiTemplates` / `DescribeTemplates`) rather than an
     * actual run (`katachiTemplate` / [me.tbsten.katachi.template.GenerateCodeFromTemplate]).
     *
     * A preview replays the block with every parameter and [captureValue] read as a stand-in
     * string, `${name}`, rather than a value someone actually passed. A template that checks such
     * a value -- refusing one that is not alphanumeric, say -- would refuse the stand-in the same
     * way it refuses a bad one from a real run, and so could never be previewed. `isPreview` is how
     * such a check tells the two apart, without the preview passing a value of its own that a real
     * run could accidentally match.
     *
     * ## Example 1: let the stand-in through, and only judge the value on a real run
     * ```kt
     * "${capture("resource")}.kt".file()
     *     .template {
     *         val resource = captureValue("resource") // "${resource}" while previewing
     *         require(isPreview || resource.all { it.isLetterOrDigit() }) {
     *             "resource must be alphanumeric, was $resource"
     *         }
     *         "// $resource"
     *     }
     * ```
     */
    public val isPreview: Boolean
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
 * "${capture("name")}UseCase.kt".file()
 *     .template {
 *         val name = captureValue("name")
 *         val implBody by stringParameter(default = """TODO("not implemented")""")
 *
 *         "// ${name}UseCase: $implBody"
 *     }
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
     * "${capture("name")}UseCase.kt".file()
     *     .template {
     *         @Suppress("UNUSED_VARIABLE")
     *         val packageName by stringParameter(default = "com.example")
     *         val name = captureValue("name")
     *
     *         "interface ${name}UseCase"
     *     }
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
     * "UseCase.kt".file()
     *     .template {
     *         val name by stringParameter()
     *
     *         // `name` is an ordinary String here, so Kotlin's own string templates are the whole
     *         // templating language.
     *         "interface ${name}UseCase"
     *     }
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
