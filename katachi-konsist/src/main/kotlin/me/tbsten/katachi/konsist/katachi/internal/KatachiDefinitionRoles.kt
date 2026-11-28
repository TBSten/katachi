package me.tbsten.katachi.konsist.katachi.internal

import me.tbsten.katachi.diff.diffWeight
import me.tbsten.katachi.dsl.GroupScope
import me.tbsten.katachi.dsl.LayoutDirectory
import me.tbsten.katachi.dsl.LayoutScope
import me.tbsten.katachi.dsl.gradle.kotlin
import me.tbsten.katachi.dsl.gradle.testSourceSet
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.dsl.template
import me.tbsten.katachi.konsist.declarations.KtFileContentScope
import me.tbsten.katachi.konsist.declarations.ktFileContent
import me.tbsten.katachi.konsist.katachi.KatachiDefinitionConfig
import me.tbsten.katachi.konsist.katachi.KatachiDefinitionRoleConfig

internal fun GroupScope.declareDefinitionRoles(config: KatachiDefinitionConfig) {
    declare(config, config.entry, ENTRY_TEXT, weighted = true, { entryLayout(config, it) }) { entryContent(config) }
    declare(config, config.group, GROUP_TEXT, weighted = true, { groupLayout(config, it) }) { groupContent(config) }
    declare(config, config.role, ROLE_TEXT, weighted = true, { roleLayout(config, it) }) { roleContent() }
    declare(config, config.architectureTest, TEST_TEXT, weighted = false, { testLayout(config, it) }) { testContent() }
}

private fun GroupScope.declare(
    config: KatachiDefinitionConfig,
    role: KatachiDefinitionRoleConfig,
    text: RoleText,
    weighted: Boolean,
    layoutOf: LayoutScope.(LayoutDirectory) -> Unit,
    recommended: KtFileContentScope.() -> Unit,
) {
    if (!role.enabled) return
    role.name.role {
        title = text.title
        summary = text.summary
        description = text.description
        // A change to the definition changes what every check and page says, so katachiDiff puts it at the top of the heat.
        if (weighted) diffWeight = 10.0
        layout { layoutOf(config.moduleDir / testSourceSet / kotlin / config.rootPackage.replace('.', '/')) }
        val written = role.content
        when {
            role.recommendedContent -> ktFileContent {
                recommended()
                written?.invoke(this)
            }
            written != null -> ktFileContent { written() }
        }
        role.additional(this)
    }
}

private fun LayoutScope.entryLayout(config: KatachiDefinitionConfig, source: LayoutDirectory) {
    source / config.entry.fileName.ktFile().template {
        """
            @file:OptIn(ExperimentalKatachiApi::class)

            package ${config.rootPackage}

            import me.tbsten.katachi.ExperimentalKatachiApi
            import me.tbsten.katachi.dsl.Architecture
            import me.tbsten.katachi.dsl.architecture
            import me.tbsten.katachi.konsist.katachi.katachiDefinition

            val ${config.entry.fileName.lowerFirst()}: Architecture = architecture {
              katachiDefinition(rootPackage = "${config.rootPackage}", moduleDir = "${config.moduleDir}")
              // TODO: call the group functions here, one per group.
            }
        """.trimIndent() + "\n"
    }
}

private fun LayoutScope.groupLayout(config: KatachiDefinitionConfig, source: LayoutDirectory) {
    // Any depth: `ui/UiGroup.kt`, and `app/api/ApiGroup.kt` for a group inside a group.
    source / "**" / config.group.fileName.ktFile()
    // The skeleton goes to the top level of `dir`: the group's directory, which is its name in lower case.
    if ('*' in config.group.fileName) {
        source / capture("dir") / config.group.fileName.replace("*", capture("name")).ktFile().template {
            val name = captureValue("name")
            val dir = captureValue("dir")
            val function = name.replaceFirstChar { it.lowercaseChar() }
            """
                package ${config.rootPackage}.$dir

                import me.tbsten.katachi.dsl.DeclarationContainerScope

                // TODO: call ${function}Group() from the entry file.
                /** TODO: say what the roles of the $name group have in common. */
                fun DeclarationContainerScope.${function}Group() = "$name".group {
                  title = "$name"
                  summary = "TODO: what the roles of this group have in common"
                }
            """.trimIndent() + "\n"
        }
    }
}

private fun LayoutScope.roleLayout(config: KatachiDefinitionConfig, source: LayoutDirectory) {
    // Any depth, and also beside the entry for a role no group calls.
    source / "**" / config.role.fileName.ktFile()
    // The skeleton goes into the directory of the group named by `--arg group=`.
    if ('*' in config.role.fileName) {
        source / capture("group") / config.role.fileName.replace("*", capture("name")).ktFile().template {
            val name = captureValue("name")
            val group = captureValue("group")
            val function = name.replaceFirstChar { it.lowercaseChar() }
            """
                package ${config.rootPackage}.$group

                import me.tbsten.katachi.dsl.DeclarationContainerScope

                // TODO: call $function() from the group this role belongs to.
                /** TODO: say what a file of the $name role is. */
                fun DeclarationContainerScope.$function() = "$name".role {
                  title = "$name"
                  summary = "TODO: what a file of this role is"
                  // TODO: declare where its files live.
                  layout { }
                }
            """.trimIndent() + "\n"
        }
    }
}

private fun LayoutScope.testLayout(config: KatachiDefinitionConfig, source: LayoutDirectory) {
    source / config.architectureTest.fileName.ktFile()
}

// The entry holds one `val` and the group files hold one function each, because a role that
// is written anywhere else is a role no `<Name>Role.kt` reader finds. Every definition file may
// opt in to the experimental API, which the DSL asks for.
private fun KtFileContentScope.entryContent(config: KatachiDefinitionConfig) {
    fileAnnotation("OptIn")
    fileAnnotation("Suppress")
    `package`(config.rootPackage)
    `import`("**")
    required(`val`, fileName.lowerFirst()).type("Architecture")
}

private fun KtFileContentScope.groupContent(config: KatachiDefinitionConfig) {
    fileAnnotation("OptIn")
    fileAnnotation("Suppress")
    `package`(matchingDirectory)
    `import`("**")
    required(`fun`, "DeclarationContainerScope", fileName.lowerFirst())()
    // A document section a group fills in (`val TestPolicy = documentSection(...)`) is a property of
    // the group file; anything else public, such as a second group, is reported.
    allow(`val` or `var`, "*")
    allow(private, anythingElse)
}

private fun KtFileContentScope.roleContent() {
    fileAnnotation("OptIn")
    fileAnnotation("Suppress")
    `package`(matchingDirectory)
    `import`("**")
    required(`fun`, "DeclarationContainerScope", fileName.removeSuffix("Role").lowerFirst())()
    allow(private, anythingElse)
}

private fun KtFileContentScope.testContent() {
    fileAnnotation("OptIn")
    fileAnnotation("Suppress")
    `package`(matchingDirectory)
    `import`("**")
    allow(anythingElse)
}

private fun String.lowerFirst(): String = replaceFirstChar { it.lowercase() }

private class RoleText(val title: String, val summary: String, val description: String)

private val ENTRY_TEXT = RoleText(
    title = "Architecture definition entry",
    summary = "The one file whose architecture { } gathers every group, so the definition is read from here",
    description = """
        `ProjectArchitecture.kt` holds `val projectArchitecture` and nothing else. It calls the
        group functions, so it declares no group or role itself: those are written in
        `<Name>Group.kt` and `<Name>Role.kt`, in the directory named after each group.
    """.trimIndent(),
)

private val GROUP_TEXT = RoleText(
    title = "Group definition",
    summary = "One `<Name>Group.kt` per group, in the directory named after the group, listing the roles it is made of",
    description = """
        A group is an extension function on `DeclarationContainerScope` that calls the role
        functions, so a role moves into another group by moving its file. The function is named
        after the file (`DomainGroup.kt` declares `domainGroup()`) and is the only function in it. A property
        such as a document section the group fills in may sit beside it.
    """.trimIndent(),
)

private val ROLE_TEXT = RoleText(
    title = "Role definition",
    summary = "One `<Name>Role.kt` per role: the extension function that declares it, and the private parts only it uses",
    description = """
        A role is an extension function on `DeclarationContainerScope`, named after the file without
        `Role` (`DslRole.kt` declares `dsl()`). What only that role needs stays `private` in the
        same file. The function carries no modifier such as `inline`: katachi captures the
        declaration site from the stack, and an inlined frame would point at a line nobody wrote.
    """.trimIndent(),
)

private val TEST_TEXT = RoleText(
    title = "Architecture test",
    summary = "The test that checks the whole project against the definition",
    description = """
        The file name is `*Test.kt`. It calls `assert(FileConstraintCheck())` on the definition: with
        a bare `assert()`, the `ktFileContent { }` constraints of the roles are reported as
        `[UncheckedFileConstraint]` instead of being evaluated.
    """.trimIndent(),
)
