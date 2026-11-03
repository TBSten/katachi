package me.tbsten.katachi.dsl

import kotlin.enums.EnumEntries
import me.tbsten.katachi.dsl.KatachiInvalidTemplateSetException.Problem
import me.tbsten.katachi.dsl.internal.DeclaredTemplateParameter
import me.tbsten.katachi.dsl.internal.InvalidTemplateValue
import me.tbsten.katachi.dsl.internal.ON_EXISTING_ARG
import me.tbsten.katachi.dsl.internal.ParsedArgValue
import me.tbsten.katachi.dsl.internal.TEMPLATE_ARG
import me.tbsten.katachi.dsl.internal.TemplateParameterBinder
import me.tbsten.katachi.dsl.internal.TemplateParameterType
import me.tbsten.katachi.dsl.internal.TemplateSetMemberDeclaration
import me.tbsten.katachi.dsl.internal.TemplateSetMemberReplay
import me.tbsten.katachi.dsl.internal.TemplateSetMode
import me.tbsten.katachi.dsl.internal.captureDeclarationSite

private val RESERVED_PARAMETER_NAMES: Set<String> = setOf(TEMPLATE_ARG, ON_EXISTING_ARG)

internal class TemplateSetScopeImpl(
    private val specifier: String,
    private val mode: TemplateSetMode,
    private val declaredAt: DeclarationSite,
) : TemplateSetScope, TemplateParameterBinder {
    override var title: String? = null
    override var summary: String? = null

    private val created = mutableListOf<TemplateParameter<*>>()
    private val named = linkedMapOf<String, TemplateParameter<*>>()
    private val duplicates = mutableListOf<Pair<TemplateParameter<*>, TemplateParameter<*>>>()
    private val members = mutableListOf<TemplateSetMemberReplay>()
    private val memberDeclarations = mutableListOf<TemplateSetMemberDeclaration>()
    private val missing = linkedSetOf<String>()
    private val invalid = linkedMapOf<String, InvalidTemplateValue>()
    private var insideMember = false

    private val values: Map<String, String> = when (mode) {
        TemplateSetMode.Declare -> emptyMap()
        is TemplateSetMode.Run -> mode.values
        is TemplateSetMode.Preview -> mode.values
    }

    override fun stringParameter(default: String?): TemplateParameter<String> =
        parameter(TemplateParameterType.StringType, default)

    override fun booleanParameter(default: Boolean?): TemplateParameter<Boolean> =
        parameter(TemplateParameterType.BooleanType, default)

    override fun intParameter(default: Int?): TemplateParameter<Int> =
        parameter(TemplateParameterType.IntType, default)

    override fun <E : Enum<E>> enumParameter(entries: EnumEntries<E>): TemplateParameter<E> {
        if (entries.isEmpty()) {
            throw KatachiEmptyEnumTemplateParameterException(
                role = specifier,
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

    override fun template(specifier: String, block: TemplateSetMemberScope.() -> Unit) {
        val site = captureDeclarationSite()
        memberDeclarations += TemplateSetMemberDeclaration(specifier, site)
        if (mode == TemplateSetMode.Declare) return
        val member = TemplateSetMemberScopeImpl(this.specifier, mode is TemplateSetMode.Preview)
        val outer = insideMember
        insideMember = true
        try {
            member.block()
        } finally {
            insideMember = outer
        }
        members += TemplateSetMemberReplay(specifier = specifier, declaredAt = site, args = member.args())
    }

    override fun bind(parameter: TemplateParameter<*>) {
        val name = parameter.name ?: return
        val first = named[name]
        if (first != null && first !== parameter) {
            duplicates += first to parameter
            return
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
        val name = parameter.name ?: return parameter.default ?: parameter.type.standIn("")
        // The members are fixed before any value is known, so a value read anywhere but inside a
        // member's block could decide them. Declare never runs a member's block at all.
        if (!insideMember) {
            throw KatachiInvalidTemplateSetException(
                templateSet = specifier,
                problem = Problem.ValueReadOutsideMember,
                name = name,
                declaredAt = captureDeclarationSite(),
                firstDeclaredAt = null,
            )
        }
        values[name]?.let { raw ->
            when (val parsed = parameter.type.parse(raw)) {
                is ParsedArgValue.Parsed -> return parsed.value
                // Already recorded by bind; this only keeps the replay going so that every other
                // problem is collected too.
                is ParsedArgValue.Invalid -> return parameter.default ?: parameter.type.standIn(name)
            }
        }
        parameter.default?.let { return it }
        missing += name
        return parameter.type.standIn(name)
    }

    fun parameters(): List<DeclaredTemplateParameter> =
        named.map { (name, parameter) ->
            DeclaredTemplateParameter(name = name, type = parameter.type, default = parameter.default)
        }

    fun memberDeclarations(): List<TemplateSetMemberDeclaration> = memberDeclarations.toList()

    fun members(): List<TemplateSetMemberReplay> = members.toList()

    fun missingNames(): List<String> = missing.toList()

    fun invalidValues(): Map<String, InvalidTemplateValue> = invalid.toMap()

    fun requireWellFormed() {
        named.entries.firstOrNull { it.key in RESERVED_PARAMETER_NAMES }?.let { (name, parameter) ->
            throw KatachiInvalidTemplateSetException(
                templateSet = specifier,
                problem = Problem.ReservedParameterName,
                name = name,
                declaredAt = parameter.declaredAt,
                firstDeclaredAt = null,
            )
        }
        duplicates.firstOrNull()?.let { (first, second) ->
            throw KatachiInvalidTemplateSetException(
                templateSet = specifier,
                problem = Problem.DuplicateParameter,
                name = second.name,
                declaredAt = second.declaredAt,
                firstDeclaredAt = first.declaredAt,
            )
        }
        created.firstOrNull { it.name == null }?.let { unnamed ->
            throw KatachiInvalidTemplateSetException(
                templateSet = specifier,
                problem = Problem.UnboundParameter,
                name = null,
                declaredAt = unnamed.declaredAt,
                firstDeclaredAt = null,
            )
        }
        if (mode == TemplateSetMode.Declare && memberDeclarations.isEmpty()) {
            throw KatachiInvalidTemplateSetException(
                templateSet = specifier,
                problem = Problem.Empty,
                name = null,
                declaredAt = declaredAt,
                firstDeclaredAt = null,
            )
        }
    }
}

internal class TemplateSetMemberScopeImpl(
    private val templateSet: String,
    override val isPreview: Boolean,
) : TemplateSetMemberScope {
    private val args = linkedMapOf<String, String>()
    private val sites = mutableMapOf<String, DeclarationSite>()

    override fun arg(name: String, value: String) {
        val site = captureDeclarationSite()
        val first = sites[name]
        if (first != null) {
            throw KatachiInvalidTemplateSetException(
                templateSet = templateSet,
                problem = Problem.DuplicateArg,
                name = name,
                declaredAt = site,
                firstDeclaredAt = first,
            )
        }
        sites[name] = site
        args[name] = value
    }

    override fun arg(name: String, value: Boolean) = arg(name, value.toString())

    override fun arg(name: String, value: Int) = arg(name, value.toString())

    override fun arg(name: String, value: Enum<*>) = arg(name, value.name)

    fun args(): Map<String, String> = args.toMap()
}
