package me.tbsten.katachi.dsl

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.internal.FileConstraintDeclaration
import me.tbsten.katachi.dsl.internal.MetadataValues
import me.tbsten.katachi.dsl.internal.get

/**
 * A role: what a file is for, and where it may live.
 *
 * What a role *is* — its name, where it was declared, where its files live — is here. What some
 * processor wants to say *about* it is metadata, read with [get]: [Title], [Summary],
 * [Description], [Documented] and [Examples] are the ones katachi ships, a processor adds its own
 * with [metadata()][me.tbsten.katachi.dsl.metadata], and whoever writes the definition adds a whole
 * section of the generated page with [documentSection].
 *
 * ## Example 1: declare a role and read it back
 * ```kt
 * val arch = architecture {
 *     "domain".group {
 *         "model".group {
 *             "UseCase".role {
 *                 title = "Use case"
 *                 summary = "A single app-specific behavior that happens on a screen"
 *                 layout { "useCase" / "*UseCase".ktFile() }
 *             }
 *         }
 *     }
 *     "Readme".role { layout { "README.md".file() } }
 * }
 *
 * val useCase = arch.allRoles.single { it.name == "UseCase" }
 * useCase.name shouldBe "UseCase"
 * useCase.groupPath shouldContainExactly listOf("domain", "model")
 * useCase.qualifiedName shouldBe "domain.model.UseCase"
 * useCase.layouts.size shouldBe 1
 * useCase[Title] shouldBe "Use case"
 * println(useCase.declaredAt) // where `"UseCase".role { }` was written
 *
 * arch.allRoles.single { it.name == "Readme" }.qualifiedName shouldBe "Readme" // a role at the root is in no group
 * ```
 */
public class Role internal constructor(
    /**
     * Identifier. Matches `[A-Za-z][A-Za-z0-9_-]*`.
     */
    public val name: String,
    internal val metadata: MetadataValues,
    /**
     * The `layout { }` blocks of this role, in declaration order.
     *
     * They have not been
     * evaluated. A role may declare several, one per place its files may live.
     */
    public val layouts: List<LayoutDeclaration>,
    internal val fileConstraints: List<FileConstraintDeclaration>,
    /**
     * Names of the groups this role sits in, outermost first.
     */
    public val groupPath: List<String>,
    /**
     * Where `"Name".role { }` was written.
     */
    public val declaredAt: DeclarationSite,
) {
    /**
     * [groupPath][Role.groupPath] and [name][Role.name] joined with `.`, e.g. `domain.UseCase`.
     */
    public val qualifiedName: String = (groupPath + name).joinToString(".")

    /**
     * The value written under [key], or `null` when this role does not carry that key.
     *
     * The declared value, as written: nothing is inherited from the groups around it. A processor
     * that wants inheritance walks [groupPath][Role.groupPath] itself, where it can say what
     * combining two values means for its own word.
     *
     * ## Example 1: read a processor's own key off a role
     * ```kt
     * val Owner: MetadataKey<String> = metadata()
     * var RoleScope.owner: String? by Owner
     *
     * val arch = architecture {
     *     "domain".group { "UseCase".role { owner = "platform" } }
     * }
     * arch.allRoles.single()[Owner] shouldBe "platform"
     * ```
     */
    @ExperimentalKatachiApi
    public operator fun <T : Any> get(key: MetadataKey<T>): T? = metadata[key]

    /**
     * The Markdown written on this role under [section], or `null` when this role did not write
     * it.
     *
     * ## Example 1: read a section off a role
     * ```kt
     * val TestPolicy = documentSection("Testing")
     * var MetadataScope.testPolicy by TestPolicy
     *
     * val arch = architecture {
     *     "api".group { "Controller".role { testPolicy = "- The status code" } }
     * }
     * arch.allRoles.single()[TestPolicy] shouldBe "- The status code"
     * ```
     */
    @ExperimentalKatachiApi
    public operator fun get(section: DocumentSection): String? = metadata[section]

    /** `Role(<qualifiedName>)`. */
    override fun toString(): String = "Role($qualifiedName)"
}
