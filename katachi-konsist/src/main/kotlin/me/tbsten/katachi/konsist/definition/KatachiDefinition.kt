package me.tbsten.katachi.konsist.definition

import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.DeclarationContainerScope
import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.GroupScope
import me.tbsten.katachi.konsist.definition.internal.KatachiDefinitionConfigImpl
import me.tbsten.katachi.konsist.definition.internal.KatachiDefinitionRoleConfigImpl
import me.tbsten.katachi.konsist.definition.internal.declareDefinitionRoles

/**
 * Declares the group that keeps the architecture test module itself in the recommended shape.
 *
 * `KatachiDefinition` holds four roles: the entry file with `val projectArchitecture`, the
 * `<Name>Group.kt` files, the `<Name>Role.kt` files and the test that asserts the definition.
 * What each file may hold is written with `ktFileContent { }`, so a role declared in the
 * entry, or a group function in a role file, is reported like any other violation.
 *
 * Every value has the recommended one as its default, and each role takes a lambda to change
 * it: its name, its file name, whether it is declared at all, and the lines its
 * `ktFileContent { }` allows. See [KatachiDefinitionRoleConfig].
 *
 * ## Example 1: the recommended shape
 * ```kt
 * @file:OptIn(ExperimentalKatachiApi::class)
 *
 * package com.example
 *
 * import me.tbsten.katachi.dsl.Architecture
 * import me.tbsten.katachi.dsl.architecture
 * import me.tbsten.katachi.konsist.definition.katachiDefinition
 *
 * val projectArchitecture: Architecture = architecture {
 *   katachiDefinition(rootPackage = "com.example")
 *   apiGroup()
 *   domainGroup()
 * }
 * ```
 *
 * ## Example 2: allow another import in group files, and cover a support file
 * ```kt
 * katachiDefinition(
 *   rootPackage = "com.example",
 *   group = { content { `import`("com.example.shared.**") } },
 * ) {
 *   "DefinitionSupport".role {
 *     layout { "architecture-test" / testSourceSet / kotlin / "com/example" / "Support".ktFile() }
 *   }
 * }
 * ```
 *
 * @param rootPackage the package of the architecture test module's source files, such as `com.example`.
 * @param moduleDir the directory of that module, relative to the project root.
 * @param entry the role of the file that holds `val projectArchitecture`.
 * @param group the role of the `<Name>Group.kt` files.
 * @param role the role of the `<Name>Role.kt` files.
 * @param architectureTest the role of the test that asserts the definition.
 * @param additional written inside the `KatachiDefinition` group, after the four roles: roles of
 *   your own for the other files of the module, such as shared support. [KatachiDefinitionConfig] is
 *   a context parameter of it.
 * @throws KatachiInvalidDefinitionArgumentException when [rootPackage], [moduleDir] or a
 *   `fileName` is not the shape its place in the layout needs.
 * @throws me.tbsten.katachi.dsl.KatachiDuplicateDeclarationException when a name in [additional]
 *   is one of the four roles' names.
 * @see KatachiDefinitionRoleConfig
 */
@ExperimentalKatachiApi
public fun DeclarationContainerScope.katachiDefinition(
    rootPackage: String,
    moduleDir: String = "architecture-test",
    entry: KatachiDefinitionRoleConfig.Scope.() -> Unit = {},
    group: KatachiDefinitionRoleConfig.Scope.() -> Unit = {},
    role: KatachiDefinitionRoleConfig.Scope.() -> Unit = {},
    architectureTest: KatachiDefinitionRoleConfig.Scope.() -> Unit = {},
    additional: context(KatachiDefinitionConfig) GroupScope.() -> Unit = {},
) {
    val declaredAt = DeclarationSite.capture()
    val config = KatachiDefinitionConfigImpl(
        rootPackage = rootPackage.also { requireValid("rootPackage", it, PACKAGE, "a package name such as com.example", declaredAt) },
        moduleDir = moduleDir.removeSuffix("/").also {
            requireValid("moduleDir", it, DIRECTORY, "a relative directory such as architecture-test", declaredAt)
        },
        entry = KatachiDefinitionRoleConfigImpl("ArchitectureDefinitionEntry", "ProjectArchitecture").apply(entry).checked("entry", declaredAt),
        group = KatachiDefinitionRoleConfigImpl("GroupDefinition", "*Group").apply(group).checked("group", declaredAt),
        role = KatachiDefinitionRoleConfigImpl("RoleDefinition", "*Role").apply(role).checked("role", declaredAt),
        architectureTest = KatachiDefinitionRoleConfigImpl("ArchitectureTest", "*Test")
            .apply(architectureTest).checked("architectureTest", declaredAt),
    )
    "KatachiDefinition".group {
        title = "Architecture definition"
        summary = "How the files that declare the architecture are laid out"
        declareDefinitionRoles(config)
        context(config) { additional() }
    }
}

private val PACKAGE = Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*")
private val DIRECTORY = Regex("[^/\\\\*\\s][^\\\\*\\s]*")
private val FILE_NAME = Regex("[A-Za-z0-9_*]+")

private fun KatachiDefinitionRoleConfigImpl.checked(argument: String, declaredAt: DeclarationSite): KatachiDefinitionRoleConfigImpl = also {
    requireValid("$argument.fileName", fileName, FILE_NAME, "a file name without .kt, such as *Group", declaredAt)
}

@OptIn(ExperimentalKatachiApi::class)
private fun requireValid(argument: String, value: String, shape: Regex, expected: String, declaredAt: DeclarationSite) {
    if (!shape.matches(value) || ".." in value) {
        throw KatachiInvalidDefinitionArgumentException(argument, value, expected, declaredAt)
    }
}
