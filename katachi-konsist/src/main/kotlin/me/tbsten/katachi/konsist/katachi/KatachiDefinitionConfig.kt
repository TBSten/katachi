package me.tbsten.katachi.konsist.katachi

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.KatachiDsl
import me.tbsten.katachi.dsl.RoleScope
import me.tbsten.katachi.konsist.declarations.KtFileContentScope

/**
 * What [katachiDefinition] was called with, read back inside its `additional` block.
 *
 * Every role of the group is described by a [KatachiDefinitionRoleConfig], so the four of them
 * are written the same way.
 *
 * ## Example 1: read the package in the `additional` block
 * ```kt
 * architecture {
 *   katachiDefinition(rootPackage = "com.example") {
 *     "DefinitionSupport".role {
 *       layout { "architecture-test/src/test/kotlin/${rootPackage.replace('.', '/')}/Support.kt".file() }
 *     }
 *   }
 * }
 * ```
 *
 * @see katachiDefinition
 */
@ExperimentalKatachiApi
public interface KatachiDefinitionConfig {
    /** The package of the architecture test module's source files, which the directories below it extend. */
    public val rootPackage: String

    /** The directory of the architecture test module, relative to the project root. */
    public val moduleDir: String

    /** The file that holds `val projectArchitecture`. */
    public val entry: KatachiDefinitionRoleConfig

    /** The `<Name>Group.kt` files. */
    public val group: KatachiDefinitionRoleConfig

    /** The `<Name>Role.kt` files. */
    public val role: KatachiDefinitionRoleConfig

    /** The test that asserts the definition. */
    public val architectureTest: KatachiDefinitionRoleConfig
}

/**
 * How one role of [katachiDefinition] is declared: what it is called, which files it covers, and
 * what its `ktFileContent { }` allows.
 *
 * Everything has a default that is the recommended shape, so a project changes only what it wants
 * different. The same shape describes all four roles, and [Scope] is what the lambda of each one
 * receives.
 *
 * ## Example 1: add an allowed import, and rename a file
 * ```kt
 * katachiDefinition(
 *   rootPackage = "com.example",
 *   group = {
 *     fileName = "*Section"
 *     content { `import`("com.example.shared.**") }
 *   },
 * )
 * ```
 *
 * ## Example 2: write the whole `ktFileContent { }` of a role yourself
 * ```kt
 * katachiDefinition(
 *   rootPackage = "com.example",
 *   role = {
 *     recommendedContent = false
 *     content {
 *       `package`(matchingDirectory)
 *       `import`("**")
 *       allow(anythingElse)
 *     }
 *   },
 * )
 * ```
 *
 * @see KatachiDefinitionConfig
 */
@ExperimentalKatachiApi
public interface KatachiDefinitionRoleConfig {
    /** The role name. The qualified name is this one inside the `KatachiDefinition` group. */
    public val name: String

    /**
     * The files of this role, without `.kt`: `*Group` covers `DomainGroup.kt`. The function a
     * `Group` or `Role` file declares is named after it.
     */
    public val fileName: String

    /** Whether the role is declared at all. With `false` its files are files no role covers. */
    public val enabled: Boolean

    /** Whether the recommended `ktFileContent { }` of this role is written. [content] is added to it. */
    public val recommendedContent: Boolean

    /**
     * Lines added to the `ktFileContent { }` of this role, or `null` when none were written. A
     * role holds one `ktFileContent { }`, so these go in the recommended block rather than a second one.
     */
    public val content: (KtFileContentScope.() -> Unit)?

    /** What else is written in the role's block: `fileConstraint { }`, metadata, `example(...)`. */
    public val additional: RoleScope.() -> Unit

    /**
     * What each role's lambda receives: [KatachiDefinitionRoleConfig] with every value settable.
     *
     * ## Example 1: turn the test role off
     * ```kt
     * katachiDefinition(rootPackage = "com.example", architectureTest = { enabled = false })
     * ```
     */
    @KatachiDsl
    public interface Scope : KatachiDefinitionRoleConfig {
        override var name: String
        override var fileName: String
        override var enabled: Boolean
        override var recommendedContent: Boolean

        /** Adds [block] to the `ktFileContent { }` of this role. Called again, it adds more lines after the first. */
        public fun content(block: KtFileContentScope.() -> Unit)

        /** Adds [block] to what the role itself holds, after the recommended description and layout. */
        public fun additional(block: RoleScope.() -> Unit)
    }
}
