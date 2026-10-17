package me.tbsten.katachi.processor.internal

import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.tbsten.katachi.KatachiDeclarationException
import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.internal.isKatachiFrame
import me.tbsten.katachi.ide.internal.DeclarationSiteJson
import me.tbsten.katachi.ide.internal.siteOf
import me.tbsten.katachi.internal.KatachiVersion
import me.tbsten.katachi.internal.catching
import me.tbsten.katachi.processor.KatachiEntryPointNotFoundException
import me.tbsten.katachi.template.internal.encodeToJson
import me.tbsten.katachi.template.internal.writeFileAtomically
import me.tbsten.katachi.util.KatachiMultipleFailuresException

/*
 * `definition-errors.json`: what the IDE plugin reads to put a definition's mistake on the line
 * that wrote it. Only a run given `--definition-errors=<path>` writes it, and the run writes it
 * every time, `errors: []` when the definition is fine.
 *
 * The contract with the plugin is the one of `definition.json`: adding a key keeps `schemaVersion`.
 */

/** The version of the shape below. */
internal const val DEFINITION_ERRORS_SCHEMA_VERSION: Int = 1

/** The whole of `definition-errors.json`. */
@Serializable
internal class DefinitionErrorsJson(
    val schemaVersion: Int,
    val katachiVersion: String,
    /** ISO-8601, UTC, to the second. */
    val generatedAt: String,
    val errors: List<DefinitionErrorJson>,
)

/** One mistake of the definition. */
@Serializable
internal class DefinitionErrorJson(
    val phase: DefinitionErrorPhase,
    /** The key of the processor that was running, for [DefinitionErrorPhase.Processor]. */
    val processor: String?,
    /** The class name of the exception. */
    val exception: String,
    /** The whole message, line breaks included. */
    val message: String,
    val declaredAt: DeclarationSiteJson?,
)

/** When in a run the definition was found wrong. Written by its serial name. */
@Serializable
internal enum class DefinitionErrorPhase {
    /** `architecture { }` threw while the generated entry point was being initialized. */
    @SerialName("entryPoint")
    EntryPoint,

    /** A processor threw or failed while it ran. */
    @SerialName("processor")
    Processor,
}

/** A mistake of the definition, and where in the run it was found. */
internal class DefinitionErrorEntry(
    val phase: DefinitionErrorPhase,
    val processor: String?,
    val exception: Throwable,
)

/**
 * Where [throwable] was written, as far as katachi can tell.
 *
 * In this order: the `declaredAt` it carries (the exceptions that know where the declaration is),
 * then the first stack frame outside katachi -- the line of the user's own lambda, which is what
 * `DeclarationSite.capture()` would answer -- and `null` when neither says.
 */
internal fun definitionErrorSiteOf(throwable: Throwable): DeclarationSite? =
    declaredAtOf(throwable) ?: firstUserFrameOf(throwable)

/** The `declaredAt` getter a katachi exception exposes, read by name; `Unknown` counts as none. */
private fun declaredAtOf(throwable: Throwable): DeclarationSite? {
    val site = catching { throwable.javaClass.getMethod("getDeclaredAt").invoke(throwable) }.getOrNull()
    return (site as? DeclarationSite)?.takeIf { it != DeclarationSite.Unknown }
}

/**
 * The first frame that is neither katachi's, the platform's nor a library's.
 *
 * The platform is skipped because an exception such as `error("...")` starts its stack inside the
 * standard library, which is no line the user wrote; `DeclarationSite.capture()` is never asked
 * from there. A library is skipped for the same reason: a template or a constraint that throws
 * from inside kotlinpoet or Konsist would otherwise name a file of theirs, one the IDE cannot
 * open from the definition's module. A class counts as a library's when it was loaded from a
 * `.jar`; the user's own definition is compiled to a directory. A class that cannot be loaded here
 * is kept, because dropping the line of a definition we cannot see is the worse mistake.
 */
private fun firstUserFrameOf(throwable: Throwable): DeclarationSite? {
    val loader = throwable.javaClass.classLoader
    val frame = throwable.stackTrace.firstOrNull {
        !isKatachiFrame(it.className) && !isPlatformFrame(it.className) && !isLibraryFrame(it.className, loader)
    } ?: return null
    val fileName = frame.fileName ?: return null
    if (frame.lineNumber < 0) return null
    return DeclarationSite(fileName = fileName, lineNumber = frame.lineNumber)
}

private val platformPackages: List<String> =
    listOf("java.", "javax.", "jdk.", "sun.", "com.sun.", "kotlin.", "kotlinx.")

private fun isPlatformFrame(className: String): Boolean = platformPackages.any(className::startsWith)

private fun isLibraryFrame(className: String, loader: ClassLoader?): Boolean {
    val location = catching {
        Class.forName(className, false, loader).protectionDomain?.codeSource?.location
    }.getOrNull()
    return location?.path?.endsWith(".jar") == true
}

/**
 * The mistakes of the definition inside [failure], one per [KatachiDeclarationException].
 *
 * An `ExceptionInInitializerError` and a [KatachiMultipleFailuresException] are opened up. A
 * [me.tbsten.katachi.KatachiCheckException] (the environment), a
 * [me.tbsten.katachi.KatachiInternalException] (katachi's own bug), a
 * [KatachiEntryPointNotFoundException] and anything else are not the
 * definition's mistake and give none, so the IDE classifies those from Gradle's output as before.
 */
internal fun definitionErrorsOf(failure: Throwable): List<KatachiDeclarationException> = when (failure) {
    // Declared a declaration exception, but it says the entry point could not be read, not that
    // the definition is wrong: the IDE classifies that one from Gradle's output as before.
    is KatachiEntryPointNotFoundException -> emptyList()
    is KatachiDeclarationException -> listOf(failure)
    is ExceptionInInitializerError -> failure.cause?.let(::definitionErrorsOf).orEmpty()
    is KatachiMultipleFailuresException -> failure.suppressed.flatMap(::definitionErrorsOf)
    else -> emptyList()
}

/**
 * The part of [failure] that holds the definition's mistakes, to throw in place of the
 * `ExceptionInInitializerError` they were wrapped in; `null` when it holds none.
 */
internal fun definitionFailureIn(failure: Throwable): Throwable? = when {
    definitionErrorsOf(failure).isEmpty() -> null
    failure is ExceptionInInitializerError -> failure.cause?.let(::definitionFailureIn)
    else -> failure
}

/**
 * Writes `definition-errors.json` to [path], next to the file first and then moved over it.
 *
 * A write that fails is one line on [warn] and nothing else: the mistake being reported is the
 * thing to see, not a second one about where it was going to be written.
 */
internal fun writeDefinitionErrors(path: String, entries: List<DefinitionErrorEntry>, warn: (String) -> Unit) {
    val json = DefinitionErrorsJson(
        schemaVersion = DEFINITION_ERRORS_SCHEMA_VERSION,
        katachiVersion = KatachiVersion.current(),
        generatedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString(),
        errors = entries.flatMap { entry ->
            definitionErrorsOf(entry.exception).map { error ->
                DefinitionErrorJson(
                    phase = entry.phase,
                    processor = entry.processor,
                    exception = error.javaClass.name,
                    message = error.message.orEmpty(),
                    declaredAt = definitionErrorSiteOf(error)?.let(::siteOf),
                )
            }
        },
    )
    try {
        writeFileAtomically(path, encodeToJson(DefinitionErrorsJson.serializer(), json)) { it }
    } catch (failure: Exception) {
        warn("[WARN] Could not write $path: ${failure::class.simpleName}: ${failure.message}")
    }
}
