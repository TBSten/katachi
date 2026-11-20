package me.tbsten.katachi.test.processor

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.io.File
import java.util.Collections
import me.tbsten.katachi.docs.GenerateDocumentation
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.FileConstraint
import me.tbsten.katachi.dsl.FileConstraintDocumentContext
import me.tbsten.katachi.dsl.FileConstraintFailure
import me.tbsten.katachi.dsl.FileConstraintSubject
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.files.FsPath
import me.tbsten.katachi.dsl.files.KatachiFileSystem
import me.tbsten.katachi.dsl.kotlin.ktFile
import me.tbsten.katachi.dsl.template
import me.tbsten.katachi.dsl.wholeTree
import me.tbsten.katachi.processor.internal.runProcessors
import me.tbsten.katachi.template.DescribeTemplates
import me.tbsten.katachi.test.docs.withTempDirectory
import me.tbsten.katachi.test.dsl.files.fakeFileSystem

/**
 * The processors of katachi's own that the Gradle plugin declares as reading no project file read
 * none.
 *
 * The plugin lets Gradle skip such a task, or restore it from the build cache, because the test
 * runtime classpath and the arguments are then its only inputs. A processor that reads the project
 * anyway gets an answer nothing invalidates: katachiDocs kept a page naming a file that was gone
 * (KATACHI-421). The plugin cannot check its declarations, so this spec does, for every processor
 * in [declaredNotToRead]. `KatachiProcessorsTest` in katachi-gradle-plugin pins the plugin's side
 * of the same list, so neither can change without the other.
 *
 * Each one runs the way its task runs it -- through `runProcessors`, with the arguments the plugin
 * passes -- over a definition that gives it every reason to look (a constraint asking for an
 * example file, captures, templates) and a project that writes down every question asked of it.
 * Any question at all fails the spec. A tree that throws instead would not do: the documentation
 * processor catches what listing the project throws and carries on with a stand-in example.
 */
class ReadsNoProjectFilesSpec : FreeSpec({
    "Gradle plugin が読まないと宣言する katachi の processor は、プロジェクトに1度も問い合わせない" - {
        for (declared in declaredNotToRead) {
            declared.key {
                withTempDirectory { output ->
                    val project = RecordingFileSystem(temptingProject())
                    val printed = mutableListOf<String>()

                    val summary = runProcessors(
                        architecture = temptingDefinition(),
                        registry = mapOf(declared.key to declared.processor),
                        processorKeys = listOf(declared.key),
                        rawArgs = declared.args(output),
                        fileSystem = project,
                        out = { printed += it },
                    )

                    withClue(printed.joinToString("\n")) { summary.failed shouldBe 0 }
                    project.questions.shouldBeEmpty()
                }
            }
        }
    }

    "この検査は、例のファイルをプロジェクトから探す docs の processor を読む側として捕まえる" {
        // Why docs is not in declaredNotToRead, and the proof that the spec above can fail.
        withTempDirectory { output ->
            val project = RecordingFileSystem(temptingProject())

            runProcessors(
                architecture = temptingDefinition(),
                registry = mapOf("docs" to GenerateDocumentation::class.java),
                processorKeys = listOf("docs"),
                rawArgs = mapOf("outputDir" to output.path),
                fileSystem = project,
                out = {},
            )

            project.questions.shouldNotBeEmpty()
            File(output, "domain/UseCase.md").readText() shouldContain "Example file: `GetUserUseCase.kt`"
        }
    }
})

/** A processor of katachi's own, by the key the Gradle plugin registers it under. */
private class DeclaredNotToRead(
    val key: String,
    val processor: Class<*>,
    /** The arguments its task runs it with, a file it writes moved below the given directory. */
    val args: (File) -> Map<String, String>,
)

/**
 * The processors `KatachiProcessors.resolvedOutputs` in katachi-gradle-plugin declares with
 * `readsProjectFiles = false` while their key still points at katachi's own class.
 */
private val declaredNotToRead: List<DeclaredNotToRead> = listOf(
    DeclaredNotToRead("internalTemplatesJson", DescribeTemplates::class.java) { output ->
        mapOf("format" to "json", "output" to File(output, "templateDescription.json").path)
    },
)

/** A definition with every reason to look at the project, and nothing else to complain about. */
private fun temptingDefinition(): Architecture = architecture {
    files = wholeTree()
    "domain".group {
        "UseCase".role {
            // No example(...): the documentation processor looks for a file of the project.
            fileConstraint("describes itself with an example file", check = AsksForAnExampleFile)
            layout { "usecase" / "${capture("name")}UseCase".ktFile() }
        }
    }
    "data".group {
        "Repository".role { layout { "repository" / "Repository.kt".file().template { "class Repository" } } }
        "NewRepository".templateSet { template("data.Repository") }
    }
}

/** A project holding a file of each role of [temptingDefinition]. */
private fun temptingProject(): KatachiFileSystem = fakeFileSystem(workingDirectory = "/repo") {
    "/repo" {
        ".git" { "HEAD"() }
        "usecase" { "GetUserUseCase.kt"() }
        "repository" { "Repository.kt"() }
    }
}

/** Rejects nothing, and names the example file it is given on its role's page. */
private object AsksForAnExampleFile : FileConstraint {
    override fun evaluate(subject: FileConstraintSubject): List<FileConstraintFailure> = emptyList()

    override fun documentSection(context: FileConstraintDocumentContext): String =
        "## Example file\n\nExample file: `${context.exampleFile.fileName}`"
}

/** [project], writing down every question asked of it -- [workingDirectory] included. */
private class RecordingFileSystem(private val project: KatachiFileSystem) : KatachiFileSystem {
    private val asked: MutableList<String> = Collections.synchronizedList(mutableListOf())

    /** Every question so far, in the order asked. */
    val questions: List<String> get() = synchronized(asked) { asked.toList() }

    override val workingDirectory: FsPath
        get() = project.workingDirectory.also { asked += "workingDirectory" }

    override fun exists(path: FsPath): Boolean = project.exists(path).also { asked += "exists($path)" }

    override fun isDirectory(path: FsPath): Boolean = project.isDirectory(path).also { asked += "isDirectory($path)" }

    override fun list(directory: FsPath): List<FsPath> = project.list(directory).also { asked += "list($directory)" }

    override fun toString(): String = "RecordingFileSystem($project)"
}
