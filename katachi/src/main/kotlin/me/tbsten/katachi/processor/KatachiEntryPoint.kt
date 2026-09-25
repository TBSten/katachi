package me.tbsten.katachi.processor

import me.tbsten.katachi.InternalKatachiApi
import me.tbsten.katachi.dsl.Architecture

/**
 * What the Gradle plugin's generated object implements, and the only thing `main()` reads
 * through reflection.
 *
 * The plugin writes `me.tbsten.katachi.generated.GeneratedKatachiEntryPoint` to
 * `build/generated/sources/katachi/test/kotlin`, from the `katachi { }` block of the applying
 * module's `build.gradle.kts`. `main()` is handed that class's fully qualified name on the
 * command line (`--entry-point=<FQCN>`), finds it with `Class.forName`, reads its singleton
 * `INSTANCE` field once, and casts it to this interface -- the one place in the whole path where
 * a name is looked up by string rather than by the Kotlin compiler.
 *
 * That single cast is why [architecture] carries a type annotation in the generated code:
 * `override val architecture: Architecture = com.example.projectArchitecture`. A user who
 * misspells the FQCN, or who points `architecture = "..."` at something that is not an
 * `Architecture`, does not find out at run time from a `ClassCastException` -- the generated
 * file fails to compile, on the module's own `compileTestKotlin`, with the FQCN or the mismatch
 * named in the error. Everything after `Class.forName` + `INSTANCE` is read through this typed
 * interface, never through reflection again.
 *
 * `@InternalKatachiApi` rather than a type a user is meant to implement by hand: this is the
 * shape the plugin's code generator produces, not a public extension point. Implementing it
 * yourself is only useful for testing `main()` itself, which is why the KDoc examples below do
 * exactly that.
 *
 * ## Example 1: the shape the Gradle plugin generates
 * ```kt
 * @OptIn(InternalKatachiApi::class)
 * object MyEntryPoint : KatachiEntryPoint {
 *     override val architecture: Architecture = architecture {
 *         "domain".group { "UseCase" { layout { "domain" / "*UseCase.kt".file() } } }
 *     }
 *     override val processors: Map<String, Class<*>> = mapOf(
 *         "layout" to LayoutCheck::class.java,
 *     )
 * }
 *
 * MyEntryPoint.processors.keys shouldContain "layout"
 * ```
 */
@InternalKatachiApi
public interface KatachiEntryPoint {
    /**
     * The definition to run processors against, read from the module's `katachi { architecture =
     * "..." }`.
     *
     * ## Example 1: an entry point built around one definition
     * ```kt
     * @OptIn(InternalKatachiApi::class)
     * object MyEntryPoint : KatachiEntryPoint {
     *     override val architecture: Architecture = architecture {
     *         "domain".group { "UseCase" { layout { "domain" / "*UseCase.kt".file() } } }
     *     }
     *     override val processors: Map<String, Class<*>> = mapOf(
     *         "layout" to LayoutCheck::class.java,
     *     )
     * }
     *
     * MyEntryPoint.architecture.allRoles.map { it.qualifiedName } shouldContain "domain/UseCase"
     * ```
     */
    public val architecture: Architecture

    /**
     * Every processor the module registered, keyed by the name it is selected with on the
     * command line (`--processor=<key>`).
     *
     * The value is a raw `Class<*>`, not an `ArchitectureProcessor<*, *>` reference: the
     * generated code only ever names a class literal (`LayoutCheck::class.java`), which compiles
     * even for a class the generated file never constructs. Turning that class into a running
     * processor -- reading `INSTANCE`, falling back to a no-arg constructor, checking it really
     * is an `ArchitectureProcessor` -- is `main()`'s job, not this interface's.
     *
     * ## Example 1: look up a registered processor by its CLI key
     * ```kt
     * @OptIn(InternalKatachiApi::class)
     * object MyEntryPoint : KatachiEntryPoint {
     *     override val architecture: Architecture = architecture {
     *         "domain".group { "UseCase" { layout { "domain" / "*UseCase.kt".file() } } }
     *     }
     *     override val processors: Map<String, Class<*>> = mapOf(
     *         "layout" to LayoutCheck::class.java,
     *     )
     * }
     *
     * MyEntryPoint.processors["layout"] shouldBe LayoutCheck::class.java
     * ```
     */
    public val processors: Map<String, Class<*>>
}
