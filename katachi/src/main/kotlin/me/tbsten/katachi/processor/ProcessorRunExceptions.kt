package me.tbsten.katachi.processor

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.KatachiDeclarationException

/**
 * The processor key the run was started for names nothing registered.
 *
 * The Gradle tasks only pass keys that are registered, so this is reached only when the
 * processor JVM is started by hand.
 *
 * ## Example 1: an unregistered processor key
 * ```sh
 * # nothing is registered as "roleNames" in architecture-test/build.gradle.kts
 * java -cp "$TEST_RUNTIME_CLASSPATH" me.tbsten.katachi.processor.internal.MainKt \
 *     --entry-point=com.example.GeneratedKatachiEntryPoint --processor=roleNames
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
                "Run one of those through its own katachi<Key> task, or register \"$key\" in " +
                    "this module's `katachi { processors { register(...) } }` first.",
            )
        }
    },
)

/**
 * A registered processor class could be neither read as a Kotlin `object` nor constructed without arguments.
 *
 * Write the processor as an `object`, or give its class a no-argument constructor.
 *
 * ## Example 1: a processor whose only constructor takes an argument
 * ```kt
 * class NeedsAnArgument(val prefix: String) : ArchitectureProcessorNoArg<Int> {
 *     override fun process(context: ArchitectureProcessNoArgContext) = runCatching { 0 }
 * }
 * ```
 *
 * ```kts
 * katachi {
 *     processors {
 *         register("needsAnArgument", "com.example.processors.NeedsAnArgument")
 *     }
 * }
 * ```
 *
 * ```sh
 * ./gradlew :architecture-test:katachiNeedsAnArgument  # fails with this exception
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
 * ## Example 1: a registration that points at the wrong class
 * ```kts
 * katachi {
 *     processors {
 *         // RoleNamesFormatter is a helper, not an ArchitectureProcessor
 *         register("roleNames", "com.example.processors.RoleNamesFormatter")
 *     }
 * }
 * ```
 *
 * ```sh
 * ./gradlew :architecture-test:katachiRoleNames  # fails with this exception
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
 * The entry point class the Gradle plugin generates could not be read.
 *
 * Run the processor through its own task, such as `./gradlew katachiDocs`, and check that
 * `katachi { architecture = ... }` is set in the module's `build.gradle.kts`.
 *
 * ## Example 1: an entry point class that was never generated
 * ```sh
 * # The katachi<Key> tasks generate it first; a JVM started by hand may not find it.
 * java -cp "$TEST_RUNTIME_CLASSPATH" me.tbsten.katachi.processor.internal.MainKt \
 *     --entry-point=com.example.NotGenerated --processor=docs
 * ```
 *
 * @property className the fully qualified name passed with `--entry-point`.
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
                "way than through a katachi<Key> task, which is the only thing that runs the " +
                "`generateKatachiEntryPoint` task first -- or this module's " +
                "`katachi { architecture = ... }` is not set, in which case the generator " +
                "writes nothing at all.",
        )
        append(
            "Run this through the processor's own task (e.g. `./gradlew katachiDocs`), and " +
                "check that " +
                "`katachi { architecture = \"com.example.projectArchitecture\" }` is set in " +
                "this module's build.gradle.kts.",
        )
    },
    cause = cause,
)

/**
 * A processor of the run was not run, because another one could not say which `--arg` names it takes.
 *
 * Every `--arg` is checked against what all processors of the run accept before any of them runs.
 * When one processor's [ArchitectureProcessor.undeclaredArgNames] throws, that check has no
 * answer, so the others are not run. The failing processor is reported with what it threw; the
 * others with this.
 *
 * ## Example 1: two processors, one of which cannot read its template
 * ```sh
 * ./gradlew :architecture-test:katachiProcessors --processor docs --processor template --arg template=Broken
 * # [FAILED] docs
 * #   Processor "docs" was not run: template failed before any processor ran, ...
 * # [FAILED] template
 * #   (what template's undeclaredArgNames threw)
 * ```
 *
 * @property key the processor that was not run.
 * @property failed the processors whose `undeclaredArgNames` threw, in the order they were selected.
 */
@ExperimentalKatachiApi
public class KatachiProcessorNotRunException internal constructor(
    public val key: String,
    public val failed: List<String>,
) : KatachiDeclarationException(
    message = buildString {
        appendLine(
            "Processor \"$key\" was not run: ${failed.joinToString(", ")} failed before any processor ran, " +
                "so the --arg names of this run could not be checked.",
        )
        append("Fix what ${failed.joinToString(", ")} reported, and run again.")
    },
)
