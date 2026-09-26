package me.tbsten.katachi.processor

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.KatachiDeclarationException

/**
 * `--processor=<key>` named a key nothing registered.
 *
 * ## Example 1: an unregistered processor key
 * ```kt
 * val thrown = shouldThrow<KatachiProcessorNotFoundException> {
 *     runProcessors(
 *         architecture = architecture { },
 *         registry = emptyMap(),
 *         processorKeys = listOf("layout"),
 *         rawArgs = emptyMap(),
 *     )
 * }
 * thrown.key shouldBe "layout"
 * ```
 *
 * @property key the key that was passed and matched nothing.
 * @property known every key registered in this module's `katachi { processors { register(...) }
 *   }`, sorted.
 */
@ExperimentalKatachiApi
public class KatachiProcessorNotFoundException internal constructor(
    public val key: String,
    public val known: Set<String>,
) : KatachiDeclarationException(
    message = buildString {
        appendLine("Unknown processor \"$key\".")
        if (known.isEmpty()) {
            appendLine(
                "No processor is registered in this module at all: `katachi { processors { " +
                    "register(...) } }` has no `register(\"$key\", \"...\")` call to match.",
            )
            append(
                "Register one in this module's build.gradle.kts, for example " +
                    "`katachi { processors { register(\"$key\", \"com.example.MyProcessor\") } } }`.",
            )
        } else {
            appendLine("Registered processors: ${known.sorted().joinToString(", ")}.")
            append(
                "Pass one of those with --processor, or register \"$key\" in this module's " +
                    "`katachi { processors { register(...) } }` first.",
            )
        }
    },
)

/**
 * A registered processor class could be neither read as a singleton nor constructed.
 *
 * [me.tbsten.katachi.processor.internal.instantiateProcessor] looks for a Kotlin `object`'s `INSTANCE` field first, and a no-argument
 * constructor second; this is thrown once both have failed.
 *
 * ## Example 1: a class with no `INSTANCE` field and no no-argument constructor
 * ```kt
 * class NeedsAnArgument(val prefix: String)
 *
 * val thrown = shouldThrow<KatachiProcessorNotInstantiableException> {
 *     instantiateProcessor(NeedsAnArgument::class.java)
 * }
 * thrown.type shouldBe NeedsAnArgument::class.java
 * ```
 *
 * @property type the registered processor class.
 */
@ExperimentalKatachiApi
public class KatachiProcessorNotInstantiableException internal constructor(
    public val type: Class<*>,
    cause: Throwable,
) : KatachiDeclarationException(
    message = buildString {
        appendLine("Could not create an instance of ${type.name}.")
        appendLine(
            "Looked for a Kotlin `object`'s INSTANCE field first, then for a no-argument " +
                "constructor -- neither was there.",
        )
        val constructorParamCounts = type.declaredConstructors.map { it.parameterCount }
        appendLine(
            if (constructorParamCounts.isEmpty()) {
                "${type.name} declares no constructor at all."
            } else {
                "${type.name} only declares constructor(s) taking " +
                    "${constructorParamCounts.sorted().joinToString(", ")} argument(s)."
            },
        )
        append(
            "Write it as `object ${type.simpleName} : ArchitectureProcessor<...>` instead of a " +
                "class, or give it a no-argument constructor.",
        )
    },
    cause = cause,
)

/**
 * A registered class was instantiated, but it is not an [ArchitectureProcessor].
 *
 * ## Example 1: a registered class that is not a processor
 * ```kt
 * class NotAProcessor
 *
 * val thrown = shouldThrow<KatachiProcessorTypeException> {
 *     instantiateProcessor(NotAProcessor::class.java)
 * }
 * thrown.type shouldBe NotAProcessor::class.java
 * ```
 *
 * @property type the registered processor class.
 */
@ExperimentalKatachiApi
public class KatachiProcessorTypeException internal constructor(
    public val type: Class<*>,
) : KatachiDeclarationException(
    message = buildString {
        appendLine("${type.name} is not an ArchitectureProcessor.")
        appendLine(
            "This module's `katachi { processors { register(...) } }` names a class that was " +
                "created successfully, but does not implement ArchitectureProcessor<*, *>.",
        )
        append("Point the registration at a class or object that implements ArchitectureProcessor.")
    },
)

/**
 * The Gradle plugin's generated [me.tbsten.katachi.processor.internal.KatachiEntryPoint] could not be read.
 *
 * ## Example 1: an entry point class that was never generated
 * ```kt
 * val thrown = shouldThrow<KatachiEntryPointNotFoundException> {
 *     loadEntryPoint("me.tbsten.katachi.generated.GeneratedKatachiEntryPoint")
 * }
 * thrown.className shouldBe "me.tbsten.katachi.generated.GeneratedKatachiEntryPoint"
 * ```
 *
 * @property className the fully qualified name `main()` was handed with `--entry-point`.
 */
@ExperimentalKatachiApi
public class KatachiEntryPointNotFoundException internal constructor(
    public val className: String,
    cause: Throwable?,
) : KatachiDeclarationException(
    message = buildString {
        appendLine("Could not read the generated entry point $className from the test runtime classpath.")
        appendLine(
            "Either it was never generated -- this class's `INSTANCE` was reached some other " +
                "way than through the `runKatachiProcessor` task, which is the only thing that " +
                "runs the `generateKatachiEntryPoint` task first -- or this module's " +
                "`katachi { architecture = ... }` is not set, in which case the generator " +
                "writes nothing at all.",
        )
        append(
            "Run this through `./gradlew runKatachiProcessor`, and check that " +
                "`katachi { architecture = \"com.example.projectArchitecture\" }` is set in " +
                "this module's build.gradle.kts.",
        )
    },
    cause = cause,
)
