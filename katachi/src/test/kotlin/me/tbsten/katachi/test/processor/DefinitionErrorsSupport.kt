package me.tbsten.katachi.test.processor

import java.io.File
import kotlin.reflect.KClass
import me.tbsten.katachi.InternalKatachiApi
import me.tbsten.katachi.dsl.Architecture
import me.tbsten.katachi.dsl.architecture
import me.tbsten.katachi.dsl.files.KatachiFileSystem
import me.tbsten.katachi.dsl.template
import me.tbsten.katachi.ide.DescribeDefinition
import me.tbsten.katachi.processor.internal.KatachiEntryPoint
import me.tbsten.katachi.processor.internal.runMain
import me.tbsten.katachi.test.dsl.files.fakeFileSystem

/*
 * Entry points and projects for the specs of `--definition-errors`: the definition each one writes
 * is the mistake its name says, and the line the mistake is on is recorded in [WrittenAt] when the
 * line runs (a failed object initialization cannot be read back off the object itself).
 */

/** The lines the entry points below write their mistake on, recorded as they run. */
internal object WrittenAt {
    @Volatile var duplicateGroup: Int = 0

    @Volatile var wildcardTemplate: Int = 0

    @Volatile var readOutsideMember: Int = 0

    @Volatile var unknownMember: Int = 0
}

internal val describeDefinition: Map<String, Class<*>> = mapOf("internalDefinitionJson" to DescribeDefinition::class.java)

/** A definition with nothing wrong. */
@OptIn(InternalKatachiApi::class)
internal object ValidEntryPoint : KatachiEntryPoint {
    override val architecture: Architecture = architecture {
        "domain".group { "UseCase".role { layout { "domain" / "*UseCase.kt".file() } } }
    }
    override val processors: Map<String, Class<*>> = describeDefinition
}

/** `"domain".group` written twice: `architecture { }` throws while the entry point is initialized. */
@OptIn(InternalKatachiApi::class)
internal object DuplicateGroupEntryPoint : KatachiEntryPoint {
    override val architecture: Architecture = architecture {
        "domain".group { }
        WrittenAt.duplicateGroup = Throwable().stackTrace.first().lineNumber + 1
        "domain".group { }
    }
    override val processors: Map<String, Class<*>> = describeDefinition
}

/** A template set that reads its parameter outside a member: thrown while the entry point is initialized. */
@OptIn(InternalKatachiApi::class)
internal object ReadOutsideMemberEntryPoint : KatachiEntryPoint {
    override val architecture: Architecture = architecture {
        "feature".group {
            "Screen".role { layout { "feature" / "Screen.kt".file().template { "" } } }
            "NewFeature".templateSet {
                val withTest by booleanParameter(default = true); WrittenAt.readOutsideMember = Throwable().stackTrace.first().lineNumber
                if (withTest) template("feature.Screen")
                template("feature.Screen")
            }
        }
    }
    override val processors: Map<String, Class<*>> = describeDefinition
}

/** `.template { }` on a name-less `*`: found when the layout is flattened, while a processor runs. */
@OptIn(InternalKatachiApi::class)
internal object WildcardTemplateEntryPoint : KatachiEntryPoint {
    override val architecture: Architecture = architecture {
        "data".group {
            "Repository".role {
                layout {
                    WrittenAt.wildcardTemplate = Throwable().stackTrace.first().lineNumber + 1
                    "repository" / "*Repository.kt".file().template { "" }
                }
            }
        }
    }
    override val processors: Map<String, Class<*>> = describeDefinition
}

/** A template set naming a template nothing declares: found when the layout is flattened. */
@OptIn(InternalKatachiApi::class)
internal object UnknownMemberEntryPoint : KatachiEntryPoint {
    override val architecture: Architecture = architecture {
        "data".group { "Repository".role { layout { "repository" / "Repository.kt".file().template { "" } } } }
        "Combo".templateSet { template("data.Missing") }; WrittenAt.unknownMember = Throwable().stackTrace.first().lineNumber
    }
    override val processors: Map<String, Class<*>> = describeDefinition
}

/** `architecture { }` throws something that is not the definition's mistake. */
@OptIn(InternalKatachiApi::class)
internal object BoomEntryPoint : KatachiEntryPoint {
    override val architecture: Architecture = architecture {
        error("boom")
    }
    override val processors: Map<String, Class<*>> = describeDefinition
}

/** A project `ValidEntryPoint` and the others are walked over. */
internal fun validProject(): KatachiFileSystem = fakeFileSystem(workingDirectory = "/repo") {
    "/repo" {
        "gradlew"()
        "domain" { "GetUseCase.kt"() }
        "data" { "UserRepository.kt"() }
    }
}

/** What one [runMain] left behind. */
internal class MainRun(
    val status: Int,
    /** What the JVM would have shown: [runMain]'s own lines, then the uncaught exception. */
    val stderr: String,
    /** What [runMain] threw; the JVM ends with status 1 on it. */
    val thrown: Throwable?,
    val definition: File,
    val errors: File,
)

/** [runMain] on [entryPoint] (named by its class: referring to the object would initialize it here, outside `main()`) over [project], writing below [root] and asking for the errors file. */
internal fun runDefinitionMain(
    entryPoint: KClass<*>,
    project: KatachiFileSystem,
    root: File,
    withErrorsFile: Boolean = true,
): MainRun {
    val definition = File(root, "definition.json")
    val errors = File(root, "definition-errors.json")
    val stderr = StringBuilder()
    val args = buildList {
        add("--entry-point=${entryPoint.java.name}")
        add("--processor=internalDefinitionJson")
        add("--arg=output=${definition.path}")
        if (withErrorsFile) add("--definition-errors=${errors.path}")
    }
    var thrown: Throwable? = null
    val status = try {
        runMain(
            args.toTypedArray(),
            err = { stderr.appendLine(it) },
            fileSystem = project,
            out = {},
            classLoader = FreshEntryPointLoader(entryPoint.java.name),
        )
    } catch (failure: Throwable) {
        thrown = failure
        stderr.append("Exception in thread \"main\" ").append(failure.stackTraceToString())
        1
    }
    return MainRun(status, stderr.toString(), thrown, definition, errors)
}

/**
 * A loader that defines [entryPointName] itself on every run and leaves everything else to its parent, so that
 * a run whose `architecture { }` throws can be repeated: a class whose initialization failed is not initialized again.
 */
private class FreshEntryPointLoader(private val entryPointName: String) : ClassLoader(FreshEntryPointLoader::class.java.classLoader) {
    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        if (name != entryPointName) return super.loadClass(name, resolve)
        synchronized(getClassLoadingLock(name)) {
            findLoadedClass(name)?.let { return it }
            val bytes = parent.getResourceAsStream(name.replace('.', '/') + ".class")!!.use { it.readBytes() }
            return defineClass(name, bytes, 0, bytes.size)
        }
    }
}
