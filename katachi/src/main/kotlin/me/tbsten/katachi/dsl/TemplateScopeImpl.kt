package me.tbsten.katachi.dsl

import kotlin.enums.EnumEntries
import me.tbsten.katachi.dsl.internal.InvalidTemplateValue
import me.tbsten.katachi.dsl.internal.ParsedArgValue
import me.tbsten.katachi.dsl.internal.TemplateParameterBinder
import me.tbsten.katachi.dsl.internal.TemplateParameterOrigin
import me.tbsten.katachi.dsl.internal.TemplateParameterType
import me.tbsten.katachi.dsl.internal.captureDeclarationSite

internal class TemplateScopeImpl(
    private val roleName: String,
    private val values: Map<String, String>,
    private val captureNames: Set<String> = emptySet(),
    override val isPreview: Boolean = false,
) : TemplateScope, TemplateParameterBinder {
    private val created = mutableListOf<TemplateParameter<*>>()

    private val named = linkedMapOf<String, TemplateParameter<*>>()

    private val missing = linkedSetOf<String>()

    // Kept apart from `missing`: a capture is a String and never decides a branch (see branchedOnStandIn).
    private val missingCaptures = linkedSetOf<String>()

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

    fun parameterNames(): Set<String> = named.keys.toSet()

    fun parameterOrigins(): Map<String, TemplateParameterOrigin> =
        named.mapValues {
            TemplateParameterOrigin(
                declaredAt = it.value.declaredAt,
                declaredWith = it.value.type.declaredWith,
                label = it.value.type.label,
                default = it.value.default,
            )
        }

    fun parameters(): List<TemplateParameter<*>> = named.values.toList()

    // A missing String is excluded: it rarely decides a branch, and counting it would hide a misspelt `--arg` as
    // an unknown name.
    fun branchedOnStandIn(): Boolean =
        invalid.isNotEmpty() ||
            missing.any { named[it]?.type != TemplateParameterType.StringType }

    // Catches `stringParameter()` written without `by`: it has no name, so no `--arg` can reach it.
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

    // Unreadable values are reported first: a stand-in may have steered the replay and caused the missing ones.
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

    fun requireEveryValuePresent(declaredAt: DeclarationSite, cause: Throwable?, capturesApart: Boolean = false) {
        if (missing.isEmpty() && (capturesApart || missingCaptures.isEmpty())) return
        val names = (if (capturesApart) missing else missing + missingCaptures).sorted()
        throw KatachiMissingTemplateParameterException(
            role = roleName,
            names = names,
            accepted = acceptedDescriptionsOf(names),
            declaredAt = declaredAt,
            cause = cause,
            missingCaptures = if (capturesApart) missingCaptures.sorted() else emptyList(),
        )
    }

    fun missingCaptureNames(): List<String> = missingCaptures.toList()

    private fun acceptedDescriptionsOf(names: List<String>): Map<String, String> =
        names.mapNotNull { name ->
            named[name]?.type?.acceptedDescription?.let { name to it }
        }.toMap()

}
