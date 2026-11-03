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

// Same contract as definition.json: adding a key keeps the schema version.

internal const val DEFINITION_ERRORS_SCHEMA_VERSION: Int = 1

@Serializable
internal class DefinitionErrorsJson(
    val schemaVersion: Int,
    val katachiVersion: String,
    val generatedAt: String,
    val errors: List<DefinitionErrorJson>,
)

@Serializable
internal class DefinitionErrorJson(
    val phase: DefinitionErrorPhase,
    val processor: String?,
    val exception: String,
    val message: String,
    val declaredAt: DeclarationSiteJson?,
)

@Serializable
internal enum class DefinitionErrorPhase {
    @SerialName("entryPoint")
    EntryPoint,

    @SerialName("processor")
    Processor,
}

internal class DefinitionErrorEntry(
    val phase: DefinitionErrorPhase,
    val processor: String?,
    val exception: Throwable,
)

// Order: the declaredAt the exception carries, then the first stack frame outside katachi, else null.
internal fun definitionErrorSiteOf(throwable: Throwable): DeclarationSite? =
    declaredAtOf(throwable) ?: firstUserFrameOf(throwable)

private fun declaredAtOf(throwable: Throwable): DeclarationSite? {
    val site = catching { throwable.javaClass.getMethod("getDeclaredAt").invoke(throwable) }.getOrNull()
    return (site as? DeclarationSite)?.takeIf { it != DeclarationSite.Unknown }
}

// Platform and library (jar-loaded) frames are skipped: a file of theirs cannot be opened from the definition's module.
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

// Only KatachiDeclarationException counts; check, internal and entry-point exceptions give none (the IDE
// classifies those from Gradle's output).
internal fun definitionErrorsOf(failure: Throwable): List<KatachiDeclarationException> = when (failure) {
    // Declared a declaration exception, but it says the entry point could not be read, not that
    // the definition is wrong: the IDE classifies that one from Gradle's output as before.
    is KatachiEntryPointNotFoundException -> emptyList()
    is KatachiDeclarationException -> listOf(failure)
    is ExceptionInInitializerError -> failure.cause?.let(::definitionErrorsOf).orEmpty()
    is KatachiMultipleFailuresException -> failure.suppressed.flatMap(::definitionErrorsOf)
    else -> emptyList()
}

internal fun definitionFailureIn(failure: Throwable): Throwable? = when {
    definitionErrorsOf(failure).isEmpty() -> null
    failure is ExceptionInInitializerError -> failure.cause?.let(::definitionFailureIn)
    else -> failure
}

// A failed write is one line on warn only: the mistake being reported is the thing to see.
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
