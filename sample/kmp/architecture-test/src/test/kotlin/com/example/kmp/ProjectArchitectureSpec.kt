package com.example.kmp

import io.kotest.core.spec.style.FreeSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.io.File
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.dsl.Documented
import me.tbsten.katachi.dsl.Examples
import me.tbsten.katachi.dsl.Summary
import me.tbsten.katachi.dsl.Title
import me.tbsten.katachi.dsl.pascalCase

@OptIn(ExperimentalKatachiApi::class)
class ProjectArchitectureSpec : FreeSpec({
    "every declared group is in the model" {
        projectArchitecture.allGroups.map { it.qualifiedName }.toSet() shouldBe
            setOf("Feature", "Ui", "Data", "Testing", "App", "Gradle", "Gradle.GradleWrapper", "Tool")
    }

    "every declared role is in the model" {
        projectArchitecture.allRoles.map { it.qualifiedName }.toSet() shouldBe
            setOf(
                "Feature.Screen",
                "Feature.ViewModel",
                "Feature.Route",
                "Feature.FeatureComponent",
                "Ui.Component",
                "Ui.Theme",
                "Ui.UiCore",
                "Ui.ComponentPreview",
                "Feature.ScreenPreview",
                "Ui.PreviewRoot",
                "Ui.Navigation",
                "Data.RepositoryInterface",
                "Data.RepositoryImplementation",
                "Data.ExpectDeclaration",
                "Data.ActualImplementation",
                "Testing.Fake",
                "Testing.Test",
                "Testing.DefinitionEntry",
                "Testing.DefinitionSections",
                "Testing.GroupDefinition",
                "Testing.RoleDefinition",
                "Testing.ProjectArchitectureTest",
                "Testing.ProjectArchitectureSpec",
                "Testing.CustomProcessor",
                "Testing.ProcessorMetadata",
                "Testing.ProcessorSpec",
                "Testing.GeneratedDocumentation",
                "Testing.LayoutSnapshot",
                "Testing.BaselineFile",
                "App.ActivityEntrypoint",
                "App.AppRoot",
                "App.AndroidManifest",
                "App.AndroidResource",
                "App.XcodeProject",
                "Gradle.SettingsScript",
                "Gradle.BuildScript",
                "Gradle.GradleProperties",
                "Gradle.VersionCatalog",
                "Gradle.DaemonJvmProperties",
                "Gradle.GradleWrapper.LauncherScript",
                "Gradle.GradleWrapper.WrapperJar",
                "Gradle.GradleWrapper.WrapperProperties",
                "Tool.Documentation",
                "Tool.Git",
            )
    }

    "groups are ordered as declared" {
        // The order is the order `ProjectArchitecture.kt` calls the group functions in,
        // which is the only thing that decides it now that every declaration lives in a
        // file of its own.
        projectArchitecture.groups.map { it.name } shouldContainExactly
            listOf("Feature", "Ui", "Data", "Testing", "App", "Gradle", "Tool")
    }

    "the declaring file name and line number are available" {
        // The real point of this test: the declaration site is read off the stack trace, so
        // it is the kind of thing that breaks only outside katachi's own test setup.
        val screen = projectArchitecture.allRoles.single { it.qualifiedName == "Feature.Screen" }
        screen.declaredAt.fileName shouldBe "ScreenRole.kt"
        (screen.declaredAt.lineNumber > 0) shouldBe true

        // Groups and layouts carry one too.
        projectArchitecture.allGroups.single { it.name == "Data" }
            .declaredAt.fileName shouldBe "DataGroup.kt"
        screen.layouts.single().declaredAt.fileName shouldBe "ScreenRole.kt"
    }

    "a declaration site points at the file determined by the declaration name" {
        // The definition is split one declaration per file and `ProjectArchitecture.kt` only
        // calls the group functions. Every declaration therefore has to point at the file it
        // is written in, never at the file that called the function -- which is what would
        // happen if the extension functions were `inline`, or if katachi's frame filter
        // stopped one level too early.
        //
        // The naming rule is checked rather than listed, so adding a role does not mean
        // editing a table here: a group named `"debug-menu"` belongs in `DebugMenuGroup.kt`,
        // using `pascalCase` -- the same conversion a template can call explicitly on a captured
        // value (`captureValue(...).pascalCase`). katachi itself never applies one on its own.
        //
        // `Gradle` and everything nested under it are left out: they are declared by
        // katachi's own `gradle()`, not by this convention -- see the dedicated test below.
        projectArchitecture.allGroups.filterNot { it.qualifiedName.isGradleGroupSubtree() }.forEach { group ->
            group.declaredAt.fileName shouldBe "${group.name.pascalCase}Group.kt"
        }
        projectArchitecture.allRoles.filterNot { it.qualifiedName.isGradleGroupSubtree() }.forEach { role ->
            role.declaredAt.fileName shouldBe "${role.name.pascalCase}Role.kt"
        }

        // Said once more from the other side: if the frame were captured one level out,
        // every declaration would collapse onto the file that called into it.
        val fileNames = (
            projectArchitecture.allGroups.map { it.declaredAt.fileName } +
                projectArchitecture.allRoles.map { it.declaredAt.fileName }
            ).distinct()

        fileNames shouldNotContain "ProjectArchitecture.kt"
    }

    "Gradle and everything under it is declared in the one place that calls gradle()" {
        // katachi walks the stack trace past its own frames to find the declaration site, so
        // the `"Gradle".group { }` written inside `gradle()` and each `name { }` in it are not
        // attributed to their own lines but to the first frame outside katachi -- the
        // `gradle()` call in `gradle/GradleGroup.kt`. This is not an exception to the
        // "one declaration per file" rule; it shows that there really is a single declaration site.
        val gradleGroup = projectArchitecture.allGroups.single { it.qualifiedName == "Gradle" }
        gradleGroup.declaredAt.fileName shouldBe "GradleGroup.kt"

        val sites = (
            projectArchitecture.allGroups.filter { it.qualifiedName.isGradleGroupSubtree() }
                .map { it.declaredAt } +
                projectArchitecture.allRoles.filter { it.qualifiedName.isGradleGroupSubtree() }
                    .map { it.declaredAt }
            ).distinct()
        sites shouldBe listOf(gradleGroup.declaredAt)
    }

    "the captured line number holds the declaration itself" {
        // The test above only proves which file each declaration claims. This one reads the
        // source back, so a one-frame shift -- landing on the `uiGroup()` call in
        // ProjectArchitecture.kt, or on the `component()` call inside `UiGroup.kt` -- fails
        // here. It is also what catches an `inline` slipping onto a group or role function,
        // since the remapped line number no longer holds the name. No line number is
        // hard-coded, so editing the definition does not break it.
        val sources = architectureDefinitionSources()

        projectArchitecture.allGroups.filterNot { it.qualifiedName.isGradleGroupSubtree() }.forEach { group ->
            val lines = requireNotNull(sources[group.declaredAt.fileName]) {
                "${group.name} declared in ${group.declaredAt.fileName}, which cannot be found"
            }
            lines[group.declaredAt.lineNumber - 1] shouldContain "\"${group.name}\""
        }
        projectArchitecture.allRoles.filterNot { it.qualifiedName.isGradleGroupSubtree() }.forEach { role ->
            val lines = requireNotNull(sources[role.declaredAt.fileName]) {
                "${role.qualifiedName} declared in ${role.declaredAt.fileName}, which cannot be found"
            }
            lines[role.declaredAt.lineNumber - 1] shouldContain "\"${role.name}\""
        }
    }

    "the Gradle and Tool groups and roles have documented = false" {
        listOf(listOf("Gradle"), listOf("Gradle", "GradleWrapper"), listOf("Tool")).forAll { groupPath ->
            projectArchitecture.allGroups.single { it.path == groupPath }[Documented] shouldBe false
            projectArchitecture.allRoles
                .filter { it.groupPath == groupPath }
                .forAll { it[Documented] shouldBe false }
        }
    }

    "a group or role that omits documented has no Documented" {
        // Reading an omission as true is the reader's job; the declaration only records "not written".
        projectArchitecture.allGroups.single { it.name == "Ui" }[Documented] shouldBe null
        projectArchitecture.allRoles
            .single { it.qualifiedName == "Data.RepositoryInterface" }[Documented] shouldBe null
    }

    "a role or group with a title uses it as the display name" {
        projectArchitecture.allRoles.single { it.qualifiedName == "Feature.Screen" }[Title] shouldBe
            "Screen"
        // The group is named `Ui` but titled `UI`, so this fails if the declared title is
        // dropped in favour of the default.
        projectArchitecture.allGroups.single { it.name == "Ui" }[Title] shouldBe "UI"
    }

    "a role without a title has no Title and its role name is the display name" {
        // `tool/Git` is the only declaration in this sample without a `title`.
        val git = projectArchitecture.allRoles.single { it.qualifiedName == "Tool.Git" }
        git[Title] shouldBe null
        (git[Title] ?: git.name) shouldBe "Git"
    }

    "the shared ui layer roles point at packages of the :ui module" {
        // `:ui:component` / `:ui:theme` / `:ui:core` were merged into the single `:ui` module.
        // If a summary kept the old module path, the generated docs would disagree with reality.
        val roles = projectArchitecture.allRoles.associateBy { it.qualifiedName }
        listOf("Ui.Component", "Ui.Theme", "Ui.UiCore", "Ui.PreviewRoot").forAll { qualifiedName ->
            val summary = requireNotNull(roles[qualifiedName]?.get(Summary)) { "$qualifiedName has no summary" }
            summary shouldContain ":ui module"
            summary shouldNotContain ":ui:"
        }
    }

    "the previews and PreviewRoot are separate roles" {
        // Roles whose names share a prefix, which is exactly where a mix-up would go
        // unnoticed. The two previews are the `@Preview` functions themselves and live beside
        // the composable they render (a screen's in its feature module, a component's in
        // `:ui`); `PreviewRoot` is the single wrapper in `:ui`.
        val roles = projectArchitecture.allRoles.associateBy { it.qualifiedName }
        val screenPreview = requireNotNull(roles["Feature.ScreenPreview"]) { "Feature.ScreenPreview is missing" }
        val componentPreview = requireNotNull(roles["Ui.ComponentPreview"]) { "Ui.ComponentPreview is missing" }
        val previewRoot = requireNotNull(roles["Ui.PreviewRoot"]) { "Ui.PreviewRoot is missing" }

        listOf(screenPreview, componentPreview).forAll { preview ->
            preview[Summary].orEmpty() shouldContain "Preview.kt"
            preview[Summary].orEmpty() shouldContain "wrapped in PreviewRoot"
        }
        previewRoot[Summary].orEmpty() shouldContain "preview package"
    }

    "the data roles point at packages of the :data module" {
        // `:data` was flat until the `user` / `platform` split. A summary that does not name
        // its package would send a reader of the generated docs to the wrong directory.
        val roles = projectArchitecture.allRoles.associateBy { it.qualifiedName }
        val packageOfRole = mapOf(
            "Data.RepositoryInterface" to "user",
            "Data.RepositoryImplementation" to "user",
            "Data.ExpectDeclaration" to "platform",
            "Data.ActualImplementation" to "platform",
        )
        packageOfRole.forAll { (qualifiedName, packageName) ->
            val summary = requireNotNull(roles[qualifiedName]?.get(Summary)) { "$qualifiedName has no summary" }
            summary shouldContain ":data module's $packageName package"
        }
    }

    "data has no role for a settings package" {
        // sample/android has a SettingsRepository and this sample does not. Naming a
        // `settings` role here would document a package that no file lives in.
        projectArchitecture.allRoles
            .filter { it.groupPath == listOf("Data") }
            .map { it.name } shouldContainExactly
            listOf("RepositoryInterface", "RepositoryImplementation", "ExpectDeclaration", "ActualImplementation")
        projectArchitecture.allRoles.forAll { role ->
            role[Summary].orEmpty() shouldNotContain "settings package"
        }
    }

    "all examples are kept in the order they were called" {
        projectArchitecture.allRoles.single { it.qualifiedName == "Data.ActualImplementation" }[Examples]
            .orEmpty().map { it.name } shouldContainExactly
            listOf("PlatformInfo.android.kt", "PlatformInfo.ios.kt")
    }

    "every role declares one place per kind of file" {
        // A role holds one kind of file. The Gradle group is left out: it is declared by
        // katachi's own `gradle()`, not by this sample.
        projectArchitecture.allRoles
            .filterNot { it.qualifiedName.isGradleGroupSubtree() }
            .filter { it.layouts.size > 1 }
            .map { it.qualifiedName } shouldBe emptyList()
    }

    "KMP-specific roles are declared" {
        // These three are what makes this sample different from sample/android.
        val roles = projectArchitecture.allRoles.associateBy { it.qualifiedName }
        roles["Data.ExpectDeclaration"] shouldNotBe null
        roles["Data.ActualImplementation"] shouldNotBe null
        roles["App.XcodeProject"] shouldNotBe null
        roles["Testing.Test"]?.get(Summary)?.contains("commonTest") shouldBe true
    }

    "the module that holds the katachi definition has roles too" {
        // The cost of moving the definition into `:architecture-test`: that module is part
        // of the repository, so it needs a role too. Its sources are the definition itself,
        // and its build script falls under the module build script role.
        val roles = projectArchitecture.allRoles.associateBy { it.qualifiedName }
        val definition = requireNotNull(roles["Testing.DefinitionEntry"]) {
            "Testing.DefinitionEntry is missing"
        }
        definition[Summary].orEmpty() shouldContain "ProjectArchitecture.kt"
        // `Gradle/BuildScript` needs no example naming it: the module key it
        // declares already covers every module katachi finds, `:architecture-test` included, and `gradle()`
        // sets no `example()` on the roles it declares.
        requireNotNull(roles["Gradle.BuildScript"]) { "Gradle.BuildScript is missing" }
    }

    "every role has at least one layout" {
        projectArchitecture.allRoles.forAll { it.layouts.isNotEmpty() shouldBe true }
    }
})

private fun architectureDefinitionSources(): Map<String, List<String>> {
    val relativePath = "src/test/kotlin/com/example/kmp"
    // `getProperty` is a platform type; the JVM always defines `user.dir`.
    val workingDir = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
    val sourceRoot = generateSequence(workingDir) { it.parentFile }
        .map { File(it, relativePath) }
        .firstOrNull { it.isDirectory }
    return requireNotNull(sourceRoot) { "$relativePath was not found in $workingDir or its parents" }
        .walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .associate { it.name to it.readLines() }
}

private fun String.isGradleGroupSubtree(): Boolean = this == "Gradle" || startsWith("Gradle.")
