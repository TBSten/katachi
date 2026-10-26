@file:Suppress("DEPRECATION")
package me.tbsten.katachi.test.ide
import java.io.File
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.DeclarationSite
import me.tbsten.katachi.dsl.FileConstraint
import me.tbsten.katachi.dsl.FileConstraintFailure
import me.tbsten.katachi.dsl.FileConstraintRange
import me.tbsten.katachi.dsl.FileConstraintSubject
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.files.KatachiFileSystem
import me.tbsten.katachi.dsl.gradle.module
import me.tbsten.katachi.dsl.internal.DeclarationExplaining
import me.tbsten.katachi.dsl.internal.DeclarationExplanation
import me.tbsten.katachi.dsl.internal.DeclarationVerdict
import me.tbsten.katachi.dsl.internal.FileDeclarationsExplanation
import me.tbsten.katachi.dsl.internal.RuleExplanation
import me.tbsten.katachi.dsl.template
import me.tbsten.katachi.dsl.wholeTree
import me.tbsten.katachi.ide.internal.DescribeDefinition
import me.tbsten.katachi.ide.internal.DefinitionJson
import me.tbsten.katachi.ide.internal.DefinitionJsonOptions
import me.tbsten.katachi.ide.internal.definitionJsonOf
import me.tbsten.katachi.processor.internal.FakeArchitectureProcessContext
import me.tbsten.katachi.processor.internal.projectWalk
import me.tbsten.katachi.template.internal.templateDescriptionOf
import me.tbsten.katachi.test.dsl.files.fakeFileSystem
import me.tbsten.katachi.test.template.withTempProject

/*
 * The synthetic definitions `DescribeDefinitionSpec` writes as JSON, the projects they are walked
 * over, and the golden files the JSON is compared with. `IdePluginDefinitionFixtureSpec` writes the
 * IDE plugin's fixtures from the same projects.
 *
 * Every constraint here is a named object rather than a lambda, so `checkClass` reads the same in
 * the golden whatever the compiler calls a lambda.
 */

/** A constraint that never rejects anything, the `custom` backend of the golden. */
internal object AcceptEverything : FileConstraint {
    override fun evaluate(subject: FileConstraintSubject): List<FileConstraintFailure> = emptyList()
}

/**
 * A constraint that explains itself as `ktFileContent { }` would, without Konsist: every file's
 * one declaration is named after the file and allowed by a line written in [SITE], except a file
 * whose name ends in `Bad`, which is unexpected and misses its line, and a file named `Broken.kt`,
 * which could not be explained.
 */
internal object ExplainByFileName : FileConstraint, DeclarationExplaining {
    val SITE: DeclarationSite = DeclarationSite("ExplainingRole.kt", 21)

    override fun evaluate(subject: FileConstraintSubject): List<FileConstraintFailure> = emptyList()

    override fun explainDeclarations(subject: FileConstraintSubject): List<FileDeclarationsExplanation> =
        subject.files.filter { it.endsWith(".kt") }.map { file ->
            val name = file.substringAfterLast('/').removeSuffix(".kt")
            val rule = RuleExplanation(text = "class $name", declaredAt = SITE)
            when {
                name == "Broken" -> FileDeclarationsExplanation(file, false, emptyList(), emptyList(), "IllegalStateException: no rules for Broken")
                name.endsWith("Bad") -> FileDeclarationsExplanation(
                    file = file,
                    declarationsChecked = true,
                    declarations = listOf(DeclarationExplanation("fun", name, 3, DeclarationVerdict.Unexpected, emptyList(), null)),
                    missing = listOf(RuleExplanation(text = "class ${name.removeSuffix("Bad")}", declaredAt = SITE)),
                    unexplainedReason = null,
                )
                else -> FileDeclarationsExplanation(
                    file = file,
                    declarationsChecked = true,
                    declarations = listOf(DeclarationExplanation("class", name, 3, DeclarationVerdict.Allowed, listOf(rule), null)),
                    missing = emptyList(),
                    unexplainedReason = null,
                )
            }
        }
}

/** A constraint that claims to explain itself and throws when asked to. */
internal object ExplainingThrows : FileConstraint, DeclarationExplaining {
    override fun evaluate(subject: FileConstraintSubject): List<FileConstraintFailure> = emptyList()

    override fun explainDeclarations(subject: FileConstraintSubject): List<FileDeclarationsExplanation> =
        throw IllegalStateException("cannot explain ${subject.role.qualifiedName}\nsecond line")
}

/**
 * `basic`: a group with a templated role whose files sit in one directory, an `anyFile()` role with
 * a `DirectOnly` constraint, an ignored directory, and a root role with two required files.
 */
internal fun basicDefinition(): Architecture = architecture {
    files = wholeTree()
    "domain".group {
        title = "Domain"
        "UseCase".role {
            title = "Use case"
            summary = "One behaviour of a screen"
            layout {
                "core/domain/src/main/kotlin/useCase" {
                    description = "Behaviour for each screen"
                    "${capture("name")}UseCase.kt".file().template { "class ${captureValue("name")}UseCase\n" }
                    fileConstraint("does nothing", check = AcceptEverything)
                }
                "core/domain/build".ignore()
            }
        }
        "Model".role {
            documented = false
            layout {
                "core/domain/src/main/kotlin/model" {
                    anyFile()
                    "mapper" { "*Mapper.kt".file() }
                    fileConstraint("only the directory itself", scope = FileConstraintRange.DirectOnly, check = AcceptEverything)
                }
            }
            fileConstraint("the whole role", check = AcceptEverything)
        }
    }
    "RootBuild".role {
        layout {
            "settings.gradle.kts".file()
            "gradlew".file()
        }
    }
}

/** The project [basicDefinition] is walked over: one file of each kind, and one nothing declares. */
internal fun basicProject(): KatachiFileSystem = fakeFileSystem(workingDirectory = "/repo/architecture-test") {
    "/repo" {
        "gradlew"()
        "settings.gradle.kts"()
        "architecture-test" { "build.gradle.kts"() }
        "core/domain/src/main/kotlin/useCase" {
            "GetUserUseCase.kt"()
            "LogoutUseCase.kt"()
            "Helper.kt"()
        }
        "core/domain/src/main/kotlin/model" {
            "User.kt"()
            "mapper" { "UserMapper.kt"() }
        }
        "core/domain/build" { "output.txt"() }
    }
}

/** `modules`: a wildcard module key with a named capture, expanded to the two modules that exist. */
internal fun modulesDefinition(): Architecture = architecture {
    files = wholeTree()
    "feature".group {
        "Screen".role {
            layout {
                ":feature:${capture("feature")}".module {
                    "src/main/kotlin" / "${capture("name")}Screen.kt".file().template(id = "screen", title = "Screen") {
                        "fun ${captureValue("name")}Screen() {}\n"
                    }
                }
            }
        }
    }
}

/** The project [modulesDefinition] is walked over. */
internal fun modulesProject(): KatachiFileSystem = fakeFileSystem(workingDirectory = "/repo/architecture-test") {
    "/repo" {
        "gradlew"()
        "architecture-test" { "build.gradle.kts"() }
        "feature/home" {
            "build.gradle.kts"()
            "src/main/kotlin" { "HomeScreen.kt"() }
        }
        "feature/settings" {
            "build.gradle.kts"()
            "src/main/kotlin" { "SettingsScreen.kt"() }
        }
    }
}

/** `explained`: one role whose constraint explains its declarations, one that does not, one that throws. */
internal fun explainedDefinition(): Architecture = architecture {
    files = wholeTree()
    "Service".role {
        layout {
            "src/service" {
                "*.kt".file()
                "*.md".file()
                fileConstraint("explains itself", check = ExplainByFileName)
                fileConstraint("does not", check = AcceptEverything)
            }
        }
    }
    "Thrower".role {
        layout {
            "src/thrower" { "*.kt".file() }
        }
        fileConstraint("throws", check = ExplainingThrows)
    }
}

/** The project [explainedDefinition] is walked over. */
internal fun explainedProject(): KatachiFileSystem = fakeFileSystem(workingDirectory = "/repo") {
    "/repo" {
        "gradlew"()
        "src/service" {
            "HealthService.kt"()
            "UserServiceBad.kt"()
            "Broken.kt"()
            "README.md"()
        }
        "src/thrower" { "Thrower.kt"() }
    }
}

/** A project with no `gradlew` and no `settings.gradle.kts`, so no root, so no walk. */
internal fun rootlessProject(): KatachiFileSystem = fakeFileSystem(workingDirectory = "/nowhere") {
    "/nowhere" { "README.md"() }
}

/** What [DescribeDefinition] writes for [this] over [fileSystem], `generatedAt` and `katachiVersion` masked. */
internal fun Architecture.definitionJsonText(
    fileSystem: KatachiFileSystem,
    args: (String) -> DescribeDefinition.Args = { output -> DescribeDefinition.Args(output = output) },
): String = withTempProject { root ->
    val output = File(root, "definition.json")
    val context = FakeArchitectureProcessContext(this, args(output.path), fileSystem)
    DescribeDefinition.process(context).getOrThrow()
    maskVolatile(output.readText())
}

/**
 * The JSON's two values that change from run to run, replaced by fixed ones. They are values of the real shape (an ISO-8601
 * instant, a version), not placeholders: the IDE plugin reads these files and a reader may not accept a time that is not one.
 */
internal fun maskVolatile(json: String): String = json
    .replace(Regex(""""generatedAt": "[^"]*""""), """"generatedAt": "$FIXED_GENERATED_AT"""")
    .replace(Regex(""""katachiVersion": "[^"]*""""), """"katachiVersion": "$FIXED_KATACHI_VERSION"""")

private const val FIXED_GENERATED_AT: String = "2026-10-04T00:30:12Z"
private const val FIXED_KATACHI_VERSION: String = "0.3.0"

/** The definition as the JSON's own types, for a spec that asserts on one part of it. */
internal fun Architecture.definitionOf(
    fileSystem: KatachiFileSystem,
    maxMatchedFiles: Int = 200,
    explainDeclarations: Boolean = true,
    maxExplainedFiles: Int = 2000,
    placementsFailure: Throwable? = null,
): DefinitionJson {
    val context = FakeArchitectureProcessContext(this, DescribeDefinition.Args(output = "unused.json"), fileSystem)
    val described = templateDescriptionOf(context, withPlacements = false)
    return definitionJsonOf(
        walk = context.projectWalk,
        templates = described.json,
        placementsFailure = placementsFailure,
        previewFailures = me.tbsten.katachi.ide.internal.TemplatePreviewFailures(described.templateFailures, described.templateSetFailures),
        options = DefinitionJsonOptions(
            maxMatchedFiles = maxMatchedFiles,
            explainDeclarations = explainDeclarations,
            maxExplainedFiles = maxExplainedFiles,
            katachiVersion = "0.0.0-test",
            generatedAt = "2026-10-05T00:00:00Z",
        ),
    )
}

/** The golden directory, found from wherever the test happens to be run. */
private fun definitionGoldenDirectory(): File {
    val fromModule = File("src/test/resources/definition/golden")
    return if (fromModule.isDirectory) fromModule else File("katachi/src/test/resources/definition/golden")
}

/** Compares [actual] with the golden [name], or rewrites it under `-Dkatachi.golden.update=true`. */
internal fun checkDefinitionGolden(name: String, actual: String) {
    val file = definitionGoldenDirectory().resolve(name)
    if (System.getProperty("katachi.golden.update").equals("true", ignoreCase = true)) {
        file.parentFile.mkdirs()
        file.writeText(actual, Charsets.UTF_8)
        return
    }
    val expected = file.takeIf { it.isFile }?.readText(Charsets.UTF_8)
        ?: throw AssertionError("No golden at ${file.path}. Run `./gradlew :katachi:test -Dkatachi.golden.update=true` once to write it.")
    if (expected != actual) {
        throw AssertionError(
            "The definition JSON differs from golden ${file.path}. Re-run with -Dkatachi.golden.update=true if the change is intended.\n" +
                "--- expected\n$expected\n--- actual\n$actual",
        )
    }
}
