package me.tbsten.katachi.processor.internal

import me.tbsten.katachi.InternalKatachiApi
import me.tbsten.katachi.dsl.Architecture

/**
 * The object the Gradle plugin generates from a module's `katachi { }` block, holding the definition and the registered processors.
 *
 * You do not implement this yourself. The plugin generates it from `katachi { architecture = ... }`
 * and `processors { register(...) }`; a misspelled definition name or a value that is not an
 * `Architecture` fails the module's `compileTestKotlin` with the name in the error.
 *
 * ## Example 1: the build script the generated object is made from
 * ```kts
 * // architecture-test/build.gradle.kts
 * katachi {
 *     architecture = "com.example.projectArchitecture"
 *     processors {
 *         register("roleNames", "com.example.processors.RoleNames")
 *     }
 * }
 * ```
 */
@InternalKatachiApi
public interface KatachiEntryPoint {
    /** The definition named by the module's `katachi { architecture = "..." }`. */
    public val architecture: Architecture

    /** Every processor the module registered, keyed by the name its `katachi<Key>` task runs it as. */
    public val processors: Map<String, Class<*>>
}
