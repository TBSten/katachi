package me.tbsten.katachi.dsl

import kotlin.enums.EnumEntries
import me.tbsten.katachi.dsl.internal.InvalidTemplateValue
import me.tbsten.katachi.dsl.internal.ParsedArgValue
import me.tbsten.katachi.dsl.internal.RenderedTemplateFile
import me.tbsten.katachi.dsl.internal.TemplateEvaluation
import me.tbsten.katachi.dsl.internal.TemplateParameterBinder
import me.tbsten.katachi.dsl.internal.TemplateParameterOrigin
import me.tbsten.katachi.dsl.internal.TemplateParameterType
import me.tbsten.katachi.dsl.internal.captureDeclarationSite

/** One `file(...)` of a template, and what it rendered to once its body was invoked. */
private class TemplateFileDeclaration(
    val name: String,
    val declaredAt: DeclarationSite,
    val content: () -> String,
) {
    var rendered: String? = null
}

/**
 * Collects one replay of a `template { }` block.
 *
 * A fresh instance per replay: the values differ from run to run, and nothing a previous run
 * bound may leak into the next one.
 */
internal class TemplateScopeImpl(
    private val roleName: String,
    private val values: Map<String, String>,
    /** The names the role's `layout { }` gave its wildcards, which [captureValue] may read. */
    private val captureNames: Set<String> = emptySet(),
) : TemplateScope, TemplateParameterBinder {
    /** Every parameter handed out, named or not. The unnamed ones are the mistake to report. */
    private val created = mutableListOf<TemplateParameter<*>>()

    /** The named ones, in declaration order. */
    private val named = linkedMapOf<String, TemplateParameter<*>>()

    private val files = mutableListOf<TemplateFileDeclaration>()

    /** Names read during this replay that had nothing to read. */
    private val missing = linkedSetOf<String>()

    /**
     * Capture names read during this replay that the run gave no value. Kept apart from [missing]
     * because a capture is a String and never decides a branch, see [branchedOnStandIn].
     */
    private val missingCaptures = linkedSetOf<String>()

    /** Values this run passed that their parameter could not read, by name, in declaration order. */
    private val invalid = linkedMapOf<String, InvalidTemplateValue>()

    override fun stringParameter(default: String?): TemplateParameter<String> =
        parameter(TemplateParameterType.StringType, default)

    override fun booleanParameter(default: Boolean?): TemplateParameter<Boolean> =
        parameter(TemplateParameterType.BooleanType, default)

    override fun intParameter(default: Int?): TemplateParameter<Int> =
        parameter(TemplateParameterType.IntType, default)

    override fun <E : Enum<E>> enumParameter(entries: EnumEntries<E>): TemplateParameter<E> {
        if (entries.isEmpty()) {
            throw KatachiEmptyEnumTemplateParameterException(
                role = roleName,
                declaredAt = captureDeclarationSite(),
            )
        }
        return parameter(TemplateParameterType.EnumType(entries), default = null)
    }

    override fun <E : Enum<E>> enumParameter(default: E): TemplateParameter<E> {
        // declaringJavaClass rather than javaClass: an entry with a body is its own subclass.
        val entries = default.declaringJavaClass.enumConstants?.asList() ?: listOf(default)
        return parameter(TemplateParameterType.EnumType(entries), default)
    }

    // Reached straight from each member and never through an inline function, so that
    // captureDeclarationSite() sees the user's own line.
    private fun <T> parameter(type: TemplateParameterType<T>, default: T?): TemplateParameter<T> =
        TemplateParameter(
            binder = this,
            type = type,
            default = default,
            declaredAt = captureDeclarationSite(),
        ).also { created += it }

    override fun captureValue(name: String): String {
        if (name !in captureNames) {
            throw KatachiUnknownTemplateCaptureException(
                role = roleName,
                name = name,
                knownNames = captureNames.sorted(),
                declaredAt = captureDeclarationSite(),
            )
        }
        values[name]?.let { return it }
        missingCaptures += name
        return TemplateParameterType.StringType.standIn(name)
    }

    override fun file(name: String, content: () -> String) {
        val declaredAt = captureDeclarationSite()
        if (!isPlainFileName(name)) {
            throw KatachiInvalidTemplateFileNameException(
                role = roleName,
                fileName = name,
                declaredAt = declaredAt,
            )
        }
        val first = files.firstOrNull { it.name == name }
        if (first != null) {
            throw KatachiDuplicateTemplateFileException(
                role = roleName,
                fileName = name,
                firstDeclaredAt = first.declaredAt,
                declaredAt = declaredAt,
            )
        }
        files += TemplateFileDeclaration(name = name, declaredAt = declaredAt, content = content)
    }

    override fun bind(parameter: TemplateParameter<*>) {
        val name = parameter.name ?: return
        val first = named[name]
        if (first != null && first !== parameter) {
            throw KatachiDuplicateTemplateParameterException(
                role = roleName,
                name = name,
                firstDeclaredAt = first.declaredAt,
                declaredAt = parameter.declaredAt,
            )
        }
        named[name] = parameter
        val raw = values[name] ?: return
        val parsed = parameter.type.parse(raw)
        if (parsed is ParsedArgValue.Invalid) {
            invalid[name] = InvalidTemplateValue(
                name = name,
                raw = raw,
                type = parameter.type,
                reason = parsed.reason,
                default = parameter.default,
                parameterDeclaredAt = parameter.declaredAt,
            )
        }
    }

    override fun <T> valueOf(parameter: TemplateParameter<T>): T {
        // Unnamed here means the caller reached `getValue` without a property, which cannot
        // happen through the DSL. `requireEveryParameterNamed` is what reports it.
        val name = parameter.name ?: return parameter.default ?: parameter.type.standIn("")
        values[name]?.let { raw ->
            when (val parsed = parameter.type.parse(raw)) {
                is ParsedArgValue.Parsed -> return parsed.value
                // Already recorded by bind, and refused when the replay ends; this only keeps the
                // replay going so that every other problem is collected too.
                is ParsedArgValue.Invalid -> return parameter.default ?: parameter.type.standIn(name)
            }
        }
        parameter.default?.let { return it }
        missing += name
        return parameter.type.standIn(name)
    }

    /** Invokes every `file { }` body. Reads inside them are what fill [missing]. */
    fun render() {
        for (file in files) file.rendered = file.content()
    }

    /** The names declared so far, which is what a caller asks for before a run. */
    fun parameterNames(): Set<String> = named.keys.toSet()

    /** Where and how each named parameter was declared, which a capture conflicting with it points at. */
    fun parameterOrigins(): Map<String, TemplateParameterOrigin> =
        named.mapValues { TemplateParameterOrigin(it.value.declaredAt, it.value.type.declaredWith) }

    /** The named parameters declared so far, in declaration order, types and defaults included. */
    fun parameters(): List<TemplateParameter<*>> = named.values.toList()

    /**
     * Whether a value that could decide a branch was replaced by a stand-in: one that could not
     * be read, or a missing one of a type other than String. A missing String only decides a
     * branch in the rare template that compares it, and treating it as one would stop a
     * misspelt `--arg nmae=User` from being reported as the unknown name it is.
     */
    fun branchedOnStandIn(): Boolean =
        invalid.isNotEmpty() ||
            missing.any { named[it]?.type != TemplateParameterType.StringType }

    /**
     * Refuses a parameter that never reached a property, which is `val name = stringParameter()`
     * with the `by` left out. Such a parameter has no name, so no `--arg` can ever reach it and
     * whatever interpolated it got the object instead of a value.
     */
    fun requireEveryParameterNamed(declaredAt: DeclarationSite) {
        val unnamed = created.filter { it.name == null }
        if (unnamed.isEmpty()) return
        throw KatachiUnboundTemplateParameterException(
            role = roleName,
            parameterSites = unnamed.map { it.declaredAt },
            declaredWith = unnamed.first().type.declaredWith,
            declaredAt = declaredAt,
        )
    }

    /**
     * Reports every value the run passed that could not be read, together with every value it
     * was missing, at once. The unreadable ones come first: a stand-in may have taken the replay
     * down a branch that is why the others went missing.
     */
    fun requireEveryValueReadable(declaredAt: DeclarationSite, cause: Throwable?) {
        if (invalid.isEmpty()) return
        val missingNames = (missing + missingCaptures).sorted()
        throw KatachiInvalidTemplateParameterValueException(
            role = roleName,
            names = invalid.keys.toList(),
            problems = invalid.values.toList(),
            missing = missingNames,
            missingAccepted = acceptedDescriptionsOf(missingNames),
            declaredAt = declaredAt,
            cause = cause,
        )
    }

    /** Reports every value the run was missing at once, rather than one per attempt. */
    fun requireEveryValuePresent(declaredAt: DeclarationSite, cause: Throwable?) {
        if (missing.isEmpty() && missingCaptures.isEmpty()) return
        val names = (missing + missingCaptures).sorted()
        throw KatachiMissingTemplateParameterException(
            role = roleName,
            names = names,
            accepted = acceptedDescriptionsOf(names),
            declaredAt = declaredAt,
            cause = cause,
        )
    }

    /** What each of [names] accepts, for the ones that do not accept anything. */
    private fun acceptedDescriptionsOf(names: List<String>): Map<String, String> =
        names.mapNotNull { name ->
            named[name]?.type?.acceptedDescription?.let { name to it }
        }.toMap()

    /** Refuses a template that produces nothing: there would be no reason to run it. */
    fun requireAtLeastOneFile(declaredAt: DeclarationSite) {
        if (files.isNotEmpty()) return
        throw KatachiEmptyTemplateException(role = roleName, declaredAt = declaredAt)
    }

    fun evaluation(): TemplateEvaluation = TemplateEvaluation(
        files = files.map {
            RenderedTemplateFile(
                fileName = it.name,
                content = it.rendered.orEmpty(),
                declaredAt = it.declaredAt,
            )
        },
    )
}

/**
 * Whether [name] is a file name rather than a path.
 *
 * Nothing that could climb out of the directory the layout chose is a file name: this is the
 * one place a template could otherwise reach a path of its own choosing, and the files it
 * writes land in the user's own source tree.
 */
private fun isPlainFileName(name: String): Boolean =
    name.isNotBlank() &&
        name != "." &&
        name != ".." &&
        name.none { it == '/' || it == '\\' || it == '\n' || it == '\r' || it == '\u0000' }
