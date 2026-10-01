package com.example

import com.example.groups.apiGroup
import com.example.groups.dataGroup
import com.example.groups.domainGroup
import com.example.groups.gradleGroup
import com.example.groups.testingGroup
import com.example.groups.toolGroup
import com.example.roles.applicationConfig
import com.example.roles.loggingConfig
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.io.File
import me.tbsten.katachi.ExperimentalKatachiApi
import me.tbsten.katachi.InternalKatachiApi
import me.tbsten.katachi.check.FileConstraintCheck
import me.tbsten.katachi.check.internal.validate
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.Documented
import me.tbsten.katachi.dsl.Examples
import me.tbsten.katachi.dsl.FileConstraintRange
import me.tbsten.katachi.dsl.FileConstraintRange.DirectOnly
import me.tbsten.katachi.dsl.FileConstraintRange.Subtree
import me.tbsten.katachi.dsl.FileSelection
import me.tbsten.katachi.dsl.Title
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.files.FsPath
import me.tbsten.katachi.dsl.files.KatachiFileSystem
import me.tbsten.katachi.dsl.files.ProjectRoot
import me.tbsten.katachi.dsl.gitTracked
import me.tbsten.katachi.dsl.gradle.*
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.dsl.pascalCase
import me.tbsten.katachi.dsl.wholeTree
import me.tbsten.katachi.konsist.konsist

/**
 * Checks that [projectArchitecture] builds into the model we expect, and that the check it
 * drives actually looks at this project.
 *
 * katachi's own integration test, not part of adopting katachi: a project that uses katachi
 * writes `ProjectArchitectureTest` and nothing else. katachi's unit tests prove the DSL and
 * the traversal work in isolation; this proves they still work when the definition is
 * written by a user, in a user's build, against a katachi resolved through a composite
 * build.
 *
 * Since the definition was split one declaration per file, it also proves the harder half:
 * declaration sites still point at the file the user wrote, even though
 * `ProjectArchitecture.kt` no longer contains a single `group` or role call of its own.
 *
 * `validate()` is `@InternalKatachiApi` on purpose - a user asserts, and does not read the
 * violations - so this file opts in where a user would not have to. Reading metadata off a
 * declaration (`role[Title]`) is `@ExperimentalKatachiApi` for a different reason: the shape
 * is still moving. Writing it in the definition - `title = "..."` - needs neither.
 */
@OptIn(InternalKatachiApi::class, ExperimentalKatachiApi::class)
class ProjectArchitectureSpec : FreeSpec({
    "every declared group is in the model" {
        projectArchitecture.allGroups.map { it.qualifiedName }.toSet() shouldBe setOf(
            "api",
            "domain",
            "data",
            "app",
            "testing",
            "Gradle",
            "Gradle.GradleWrapper",
            "tool",
        )
    }

    "every declared role is in the model, with its group path" {
        projectArchitecture.allRoles.map { it.qualifiedName }.toSet() shouldBe setOf(
            "api.Controller",
            "api.KtorPlugin",
            "domain.Service",
            "domain.Model",
            "data.Repository",
            "app.Entrypoint",
            "app.ApplicationConfig",
            "app.LoggingConfig",
            "testing.Test",
            "testing.ArchitectureDefinitionEntry",
            "testing.DocumentSectionDefinition",
            "testing.GroupDefinition",
            "testing.RoleDefinition",
            "testing.ProcessorDefinition",
            "testing.ArchitectureTest",
            "testing.IntegrationSpec",
            "testing.GeneratedDocumentation",
            "testing.LayoutSnapshot",
            "testing.BaselineFile",
            "Gradle.GradleWrapper.LauncherScript",
            "Gradle.GradleWrapper.WrapperJar",
            "Gradle.GradleWrapper.WrapperProperties",
            "Gradle.SettingsScript",
            "Gradle.BuildScript",
            "Gradle.GradleProperties",
            "Gradle.VersionCatalog",
            "Gradle.DaemonJvmProperties",
            "tool.Documentation",
            "tool.Git",
        )
    }

    "a role's declaration site is the line in the file of the extension function that wrote it" {
        // Guards the stack-trace based capture in a real user build: if the frame filter
        // ever starts skipping user code, this reports the test runner's file instead.
        val controller = projectArchitecture.allRoles.single { it.qualifiedName == "api.Controller" }
        controller.declaredAt.fileName shouldBe "ControllerRole.kt"
        (controller.declaredAt.lineNumber > 0) shouldBe true
    }

    "a group's declaration site is also the file of the extension function that wrote it" {
        val api = projectArchitecture.allGroups.single { it.qualifiedName == "api" }
        api.declaredAt.fileName shouldBe "ApiGroup.kt"
    }

    "declaration sites point at the file that wrote the declaration, not at ProjectArchitecture.kt" {
        // The point of splitting the definition: `ProjectArchitecture.kt` only calls the
        // group functions, so nothing may be attributed to it. If katachi captured the frame
        // one level out, every declaration would collapse onto this one file.
        val files = (projectArchitecture.allGroups.map { it.declaredAt } + projectArchitecture.allRoles.map { it.declaredAt })
            .map { it.fileName }
            .distinct()

        files shouldNotContain "ProjectArchitecture.kt"
    }

    "each group is declared in the file determined by its name" {
        // The naming rule is the whole convention, so it is checked rather than listed: a
        // group named `"debug-menu"` belongs in `DebugMenuGroup.kt`, using `pascalCase` -- the
        // same conversion a layout can call explicitly on a captured value
        // (`capture("name").pascalCase`). katachi itself never applies one on its own.
        //
        // `Gradle` and everything nested under it are left out: they are declared by
        // katachi's own `gradle()`, not by this convention. The next test checks what they
        // do have instead.
        projectArchitecture.allGroups.filterNot { it.qualifiedName.isGradleGroupSubtree() }.forEach { group ->
            group.declaredAt.fileName shouldBe "${group.name.pascalCase}Group.kt"
        }
    }

    "each role is declared in the file determined by its name" {
        projectArchitecture.allRoles.filterNot { it.qualifiedName.isGradleGroupSubtree() }.forEach { role ->
            role.declaredAt.fileName shouldBe "${role.name.pascalCase}Role.kt"
        }
    }

    "the declaration is actually written on the captured line" {
        // The tests above only prove the file name and that the line is positive. This one
        // reads the source back, so a one-frame shift lands on `architecture {` or on the
        // `apiGroup()` call in ProjectArchitecture.kt and fails. It is also what catches an
        // `inline` slipping onto a group or role function, since the remapped line number no
        // longer holds the name. No line number is hard-coded, so editing the definition does
        // not break it.
        projectArchitecture.allGroups.filterNot { it.qualifiedName.isGradleGroupSubtree() }.forEach { group ->
            val source = sourceLinesOf(group.declaredAt.fileName)
            source[group.declaredAt.lineNumber - 1] shouldContain "\"${group.name}\""
        }
        projectArchitecture.allRoles.filterNot { it.qualifiedName.isGradleGroupSubtree() }.forEach { role ->
            val source = sourceLinesOf(role.declaredAt.fileName)
            source[role.declaredAt.lineNumber - 1] shouldContain "\"${role.name}\""
        }
    }

    "Gradle and everything under it is declared at the single site that calls gradle()" {
        // katachi walks the stack trace past its own frames to capture the declaration site, so
        // the `"Gradle".group { }` written inside `gradle()` and every `name { }` inside it
        // are attributed not to their own lines but to the first frame outside katachi -- the
        // `gradle()` call in `groups/GradleGroup.kt`. This is not an exception to the "one
        // declaration, one file" rule; it shows that there really is only one declaration site.
        val gradleGroup = projectArchitecture.allGroups.single { it.qualifiedName == "Gradle" }
        gradleGroup.declaredAt.fileName shouldBe "GradleGroup.kt"

        val sites = (
            projectArchitecture.allGroups.filter { it.qualifiedName.isGradleGroupSubtree() }
                .map { it.declaredAt } +
                projectArchitecture.allRoles.filter { it.qualifiedName.isGradleGroupSubtree() }
                    .map { it.declaredAt }
            ).distinct()
        sites shouldBe listOf(gradleGroup.declaredAt)

        val source = sourceLinesOf(gradleGroup.declaredAt.fileName)
        source[gradleGroup.declaredAt.lineNumber - 1] shouldContain "gradle()"
    }

    "only groups marked documented = false are documented = false" {
        // A declaration that did not write it has no Documented entry. Reading an omission as true happens here (the reading side).
        projectArchitecture.allGroups
            .filterNot { it[Documented] ?: true }
            .map { it.qualifiedName } shouldBe listOf("Gradle", "Gradle.GradleWrapper", "tool")
    }

    "only roles marked documented = false are documented = false" {
        projectArchitecture.allRoles
            .filterNot { it[Documented] ?: true }
            .map { it.qualifiedName } shouldBe listOf(
            "Gradle.SettingsScript",
            "Gradle.BuildScript",
            "Gradle.GradleProperties",
            "Gradle.VersionCatalog",
            "Gradle.DaemonJvmProperties",
            "Gradle.GradleWrapper.LauncherScript",
            "Gradle.GradleWrapper.WrapperJar",
            "Gradle.GradleWrapper.WrapperProperties",
            "tool.Documentation",
        )
    }

    "a role that did not omit title has the display name it specified" {
        val model = projectArchitecture.allRoles.single { it.qualifiedName == "domain.Model" }
        model[Title] shouldBe "Model"
        model[Examples].orEmpty().map { it.name } shouldBe listOf("Health")
    }

    "every role has exactly one layout" {
        projectArchitecture.allRoles.filter { it.layouts.size != 1 } shouldBe emptyList()
    }

    "in a definition missing one role, the files that role covered become Unexpected" {
        // "assert() passes" alone cannot tell a working check from one that walks nothing:
        // an empty traversal passes just as happily. Dropping `app/Entrypoint` leaves
        // `Application.kt` in a directory other roles still claim, so the file itself has to
        // be reached and matched for this to fail - which is the part being proven here.
        // `FileConstraintCheck()` is passed so the `konsist { }` constraints are evaluated
        // rather than reported as `[UncheckedFileConstraint]` alongside it.
        // `validate()` does not read the baseline, so the two violations it holds back are here too.
        architectureWithoutEntrypointRole.validate(FileConstraintCheck()).map { "[${it.label}] ${it.path}" }
            .shouldContainExactlyInAnyOrder(
                listOf("[UnexpectedFile] src/main/kotlin/com/example/Application.kt") + violationsHeldBackByBaseline,
            )
    }

    "gitTracked() / wholeTree() can be written from a user's build too" {
        // They are context parameter extensions rather than `ArchitectureScope` members, and
        // this build enables no compiler flag for them: an import is the whole cost. Proving
        // it here rather than in katachi's own specs is the point, because `:katachi` compiles
        // itself with settings a user's build does not have.
        architecture { files = gitTracked() }.files shouldBe FileSelection.GitTracked
        architecture { files = wholeTree() }.files shouldBe FileSelection.WholeTree
    }

    "a FileSelection implemented by the user decides the set of files actually walked" {
        // `FileSelection` is an ordinary interface, so a project whose files are listed by
        // something other than git writes its own. The first assertion keeps the second from
        // passing vacuously: over the real tree an empty definition reports plenty.
        architecture { }.validate().isNotEmpty() shouldBe true

        val overNothing = architecture { files = SelectsNothing }
        overNothing.files shouldBe SelectsNothing
        overNothing.validate() shouldBe emptyList()
    }

    "a konsist with scope = DirectOnly does not descend into groups/ and roles/, and with Subtree it descends and fails there" {
        // A self-contained definition, not one of this sample's roles: a user's roles should not
        // need `DirectOnly`. Passing proves nothing on its own, since a constraint that covered
        // nothing would pass too. Taking the flag away has to make the same rule reach `groups/`
        // and `roles/`, where every file declares exactly what it forbids.
        fun unsatisfiedPaths(scope: FileConstraintRange): List<String> = architecture {
            "testing".group {
                "Definition" {
                    layout {
                        "architecture-test" {
                            testSourceSet / kotlin / "com/example" {
                                "Must not declare a group or a role at the top of com.example".konsist(scope = scope) {
                                    functions().mustNot { it.receiverType?.name == "DeclarationContainerScope" }
                                }
                                "*".ktFile()
                                "**" / "*".ktFile()
                            }
                        }
                    }
                }
            }
        }.validate(FileConstraintCheck())
            .filter { it.label == "UnsatisfiedFileConstraint" }
            .map { it.path }

        unsatisfiedPaths(DirectOnly) shouldBe emptyList()
        unsatisfiedPaths(Subtree) shouldContain
            "architecture-test/src/test/kotlin/com/example/roles/ServiceRole.kt"
    }

    "a correct definition reports nothing but the violations held back by the baseline" {
        // The same run ProjectArchitectureTest makes, read as a list rather than as a thrown
        // error, so a failure here names the violations instead of only the message.
        // `FileConstraintCheck()` matches what `ProjectArchitectureTest` itself passes: without
        // it, the `konsist { }` constraints in `roles/ServiceRole.kt` and
        // `roles/ArchitectureDefinitionEntryRole.kt` would come back as
        // `[UncheckedFileConstraint] reason=NotEvaluated` instead of being evaluated.
        //
        // `validate()` does not read the baseline, so what `katachi-baseline.json` holds back
        // is all that may come back: the two violations left in on purpose, and nothing else.
        projectArchitecture.validate(FileConstraintCheck()).map { "[${it.label}] ${it.path}" }
            .shouldContainExactlyInAnyOrder(violationsHeldBackByBaseline)
    }
})

/**
 * A [FileSelection] written the way a project outside katachi writes one: it hands the walk a
 * view in which the project holds no files at all.
 *
 * `fileSystemFor` is `@ExperimentalKatachiApi` because [KatachiFileSystem] and [ProjectRoot]
 * are, so implementing it costs an explicit opt-in — the same one a user takes on to write
 * their own `FileSelection`, which is exactly what this sample stands in for. Nothing else
 * here is katachi's privilege: `GitTracked` and `WholeTree` implement the same interface with
 * the same one method.
 */
@OptIn(InternalKatachiApi::class, ExperimentalKatachiApi::class)
private object SelectsNothing : FileSelection {
    override fun fileSystemFor(
        delegate: KatachiFileSystem,
        projectRoot: ProjectRoot,
    ): KatachiFileSystem = object : KatachiFileSystem by delegate {
        override fun list(directory: FsPath): List<FsPath> = emptyList()
    }
}

/**
 * The violations left in the project on purpose, as the demo of `baseline()`, and
 * recorded in `katachi-baseline.json`. `validate()` reports them; `assert()` holds them back.
 */
private val violationsHeldBackByBaseline: List<String> = listOf(
    "[UnexpectedFile] src/main/kotlin/com/example/service/LegacyHealthCheck.kt",
    "[UnsatisfiedFileConstraint] src/main/kotlin/com/example/service/LegacyStatusService.kt",
)

/** `"Gradle"` itself or anything nested under it -- see `groups/GradleGroup.kt`. */
private fun String.isGradleGroupSubtree(): Boolean = this == "Gradle" || startsWith("Gradle.")

/**
 * [projectArchitecture] with `app/Entrypoint` removed, and nothing else changed.
 *
 * Deliberately broken, and deliberately kept out of `ProjectArchitecture.kt`: a reader
 * looking for the definition to copy should never meet it. Splitting one role per file pays
 * for itself here — the `app` group is rebuilt by calling `applicationConfig()` and `loggingConfig()`, so the
 * roles it keeps are the real ones and only the omission is written out. Before the split, `appRoles()`
 * declared both roles at once and the remaining one had to be copied by hand, where it could
 * drift away from the definition it was standing in for.
 */
private val architectureWithoutEntrypointRole: Architecture = architecture {
    apiGroup()
    domainGroup()
    dataGroup()
    "app".group {
        applicationConfig()
        loggingConfig()
    }
    testingGroup()
    gradleGroup()
    toolGroup()
}

/** Cached so that reading a source file once per declaration does not hit the disk again. */
private val sourceLineCache = mutableMapOf<String, List<String>>()

/**
 * The lines of one file of the architecture definition, so a captured line number can be
 * compared against what is actually written there.
 *
 * A test task's working directory is its module directory - here
 * `sample/jvm/architecture-test` - but that is a default a build file can change, so the
 * source root is looked up by walking up from wherever the tests run, and the file itself
 * by name, so moving a role into another package needs no change here.
 */
private fun sourceLinesOf(fileName: String): List<String> = sourceLineCache.getOrPut(fileName) {
    // `getProperty` is a platform type; the JVM always defines `user.dir`.
    val workingDir = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
    val sourceRoot = generateSequence(workingDir) { it.parentFile }
        .map { File(it, "src/test/kotlin") }
        .firstOrNull { it.isDirectory }
    requireNotNull(sourceRoot) { "src/test/kotlin not found in $workingDir or its parents" }

    val source = sourceRoot.walkTopDown().firstOrNull { it.isFile && it.name == fileName }
    requireNotNull(source) { "$fileName not found under $sourceRoot" }.readLines()
}
