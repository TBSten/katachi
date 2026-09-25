package me.tbsten.katachi.dsl

import me.tbsten.katachi.catching

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
    private val mode: TemplateEvaluationMode,
) : TemplateScope, TemplateParameterBinder {
    /** Every parameter handed out, named or not. The unnamed ones are the mistake to report. */
    private val created = mutableListOf<TemplateParameter>()

    /** The named ones, in declaration order. */
    private val named = linkedMapOf<String, TemplateParameter>()

    private val files = mutableListOf<TemplateFileDeclaration>()

    /** Names read during a [TemplateEvaluationMode.Render] replay that had nothing to read. */
    private val missing = linkedSetOf<String>()

    override fun stringParameter(default: String?): TemplateParameter =
        TemplateParameter(
            binder = this,
            default = default,
            declaredAt = captureDeclarationSite(),
        ).also { created += it }

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

    override fun bind(parameter: TemplateParameter) {
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
    }

    override fun valueOf(parameter: TemplateParameter): String {
        // Unnamed here means the caller reached `getValue` without a property, which cannot
        // happen through the DSL. `requireEveryParameterNamed` is what reports it.
        val name = parameter.name ?: return parameter.default.orEmpty()
        values[name]?.let { return it }
        parameter.default?.let { return it }
        if (mode == TemplateEvaluationMode.Render) missing += name
        // A stand-in rather than an empty string, so that a body doing something with the value
        // keeps working long enough for every other missing name to be collected too.
        return name
    }

    /** Invokes every `file { }` body. Reads inside them are what fill [missing]. */
    fun render() {
        for (file in files) file.rendered = file.content()
    }

    /** The names declared so far, which is what a caller asks for before a run. */
    fun parameterNames(): Set<String> = named.keys.toSet()

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
            declaredAt = declaredAt,
        )
    }

    /** Reports every value the run was missing at once, rather than one per attempt. */
    fun requireEveryValuePresent(declaredAt: DeclarationSite, cause: Throwable?) {
        if (missing.isEmpty()) return
        throw KatachiMissingTemplateParameterException(
            role = roleName,
            names = missing.sorted(),
            declaredAt = declaredAt,
            cause = cause,
        )
    }

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

/**
 * Replays [declaration] with [values] bound and produces its files.
 *
 * The block runs first and is only then judged, so that every missing value is known before
 * anything is said about any of them. A failure raised by the user's own code is held back for
 * the same reason: when values were missing, that is almost always why it failed, and the
 * missing names are the more useful thing to say.
 */
internal fun evaluateTemplate(
    declaration: TemplateDeclaration,
    roleName: String,
    values: Map<String, String>,
): TemplateEvaluation {
    val scope = TemplateScopeImpl(
        roleName = roleName,
        values = values,
        mode = TemplateEvaluationMode.Render,
    )
    var failure = catching { declaration.block(scope) }.exceptionOrNull()
    if (failure == null) failure = catching { scope.render() }.exceptionOrNull()
    scope.requireEveryParameterNamed(declaration.declaredAt)
    scope.requireEveryValuePresent(declaration.declaredAt, failure)
    failure?.let { throw it }
    scope.requireAtLeastOneFile(declaration.declaredAt)
    return scope.evaluation()
}

/**
 * The `--arg` names [declaration] accepts, without rendering anything.
 *
 * Asked before any processor runs, to decide which arguments of that run are known. It never
 * throws: a template that cannot answer answers with what it managed to name, because refusing
 * here would fail a run over a template nobody selected.
 */
internal fun templateParameterNames(
    declaration: TemplateDeclaration,
    roleName: String,
): Set<String> {
    val scope = TemplateScopeImpl(
        roleName = roleName,
        values = emptyMap(),
        mode = TemplateEvaluationMode.Names,
    )
    catching { declaration.block(scope) }
    return scope.parameterNames()
}
