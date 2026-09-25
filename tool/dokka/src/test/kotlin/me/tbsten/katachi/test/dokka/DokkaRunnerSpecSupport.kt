package me.tbsten.katachi.test.dokka

import me.tbsten.katachi.dokka.KatachiDokkaPlugin
import org.jetbrains.dokka.DokkaConfiguration
import org.jetbrains.dokka.DokkaConfigurationImpl
import org.jetbrains.dokka.DokkaGenerator
import org.jetbrains.dokka.DokkaModuleDescriptionImpl
import org.jetbrains.dokka.PluginConfigurationImpl
import org.jetbrains.dokka.base.testApi.testRunner.BaseAbstractTest
import org.jetbrains.dokka.model.DModule
import org.jetbrains.dokka.pages.RootPageNode
import org.jetbrains.dokka.testApi.logger.TestLogger
import org.jetbrains.dokka.utilities.DokkaConsoleLogger
import org.jetbrains.dokka.utilities.LoggingLevel
import testApi.testRunner.dokkaConfiguration
import utils.TestOutputWriterPlugin
import java.io.File
import java.nio.file.Files

/** What one single-module Dokka run produced, captured at the stages the specs look at. */
internal class DokkaRun(
    /** The documentation model after every documentable transformer, this plugin's included. */
    val module: DModule,
    /** The page tree after every page transformer, before the HTML preprocessors. */
    val pages: RootPageNode,
    /** Every file the renderer wrote, by path relative to the output root. */
    val files: Map<String, String>,
)

/**
 * Runs Dokka the way its own tests do, on inline sources, with this plugin installed.
 *
 * The plugin is not passed as an override: it is on the test classpath with its service file,
 * so Dokka's `ServiceLoader` installs it exactly as in a real run. Passing it as well would
 * install it twice.
 */
internal object DokkaRunner : BaseAbstractTest(TestLogger(DokkaConsoleLogger(LoggingLevel.WARN))) {
    /**
     * The configuration of a single JVM module with its sources under `src/main/kotlin`.
     *
     * [includes] are Markdown files of the inline sources holding the module and package docs;
     * [pluginJson] is this plugin's configuration, as `pluginsConfiguration` would pass it.
     */
    fun configuration(
        moduleName: String = "root",
        delayTemplateSubstitution: Boolean = false,
        includes: List<String> = emptyList(),
        pluginJson: String? = null,
    ): DokkaConfigurationImpl = dokkaConfiguration {
        this.moduleName = moduleName
        this.delayTemplateSubstitution = delayTemplateSubstitution
        pluginsConfigurations = pluginConfigurations(pluginJson)
        sourceSets {
            sourceSet {
                sourceRoots = listOf("src/main/kotlin")
                classpath = listOf(kotlinStdlibJar.absolutePath)
                this.includes = includes
            }
        }
    }

    /** Generates [source] into memory and returns every stage the specs look at. */
    fun run(source: String, configuration: DokkaConfigurationImpl = configuration()): DokkaRun {
        val writer = TestOutputWriterPlugin()
        var module: DModule? = null
        var pages: RootPageNode? = null
        testInline(source, configuration, pluginOverrides = listOf(writer)) {
            documentablesTransformationStage = { module = it }
            pagesTransformationStage = { pages = it }
        }
        return DokkaRun(
            module = module ?: error("Dokka never reached the documentables transformation stage"),
            pages = pages ?: error("Dokka never reached the pages transformation stage"),
            files = writer.writer.contents,
        )
    }
}

/**
 * Generates each of [modules] on disk as its own delayed-substitution run, then aggregates them
 * into the returned directory with the all-modules-page plugin — the two halves of a
 * multi-module build of the Dokka Gradle plugin.
 *
 * [dependencies] lists, by module name, the modules — or [libraries], which are never
 * documented — whose declarations a module can refer to. Their sources are analysed with the
 * module's but suppressed, so that a `[Declaration]` link to them resolves to a DRI that has no
 * page in the module's own run, as with a library on the classpath. [modulePluginJson] is this
 * plugin's configuration for every module's run, and [aggregatedPluginJson] for the aggregating
 * one.
 *
 * The all-modules-page plugin is handed over as `pluginsClasspath`, never put on the test
 * classpath, so only the aggregating run loads it (see `tool/dokka/build.gradle.kts`).
 */
internal fun generateMultiModule(
    workDirectory: File,
    modules: Map<String, String>,
    aggregatedPluginJson: String? = null,
    modulePluginJson: String? = null,
    dependencies: Map<String, List<String>> = emptyMap(),
    libraries: Map<String, String> = emptyMap(),
): File {
    val sourceRoots = (modules + libraries).mapValues { (name, source) ->
        File(workDirectory, "sources/$name").also { writeInlineSources(source, it) }.resolve("src/main/kotlin")
    }
    val descriptions = modules.keys.map { name ->
        val output = File(workDirectory, "modules/$name")
        val dependencyRoots = dependencies[name].orEmpty().map { sourceRoots.getValue(it) }
        val configuration = dokkaConfiguration {
            moduleName = name
            outputDir = output
            delayTemplateSubstitution = true
            pluginsConfigurations = pluginConfigurations(modulePluginJson)
            sourceSets {
                sourceSet {
                    this.sourceRoots = (listOf(sourceRoots.getValue(name)) + dependencyRoots).map { it.absolutePath }
                    suppressedFiles = dependencyRoots.flatMap { root -> root.walkTopDown().filter { it.isFile }.map { it.absolutePath }.toList() }
                    classpath = listOf(kotlinStdlibJar.absolutePath)
                }
            }
        }
        DokkaGenerator(configuration, quietLogger).generate()
        DokkaModuleDescriptionImpl(
            name = name,
            relativePathToOutputDirectory = File(name),
            includes = emptySet(),
            sourceOutputDirectory = output,
        )
    }

    val aggregated = File(workDirectory, "aggregated")
    val configuration = dokkaConfiguration {
        moduleName = "project"
        outputDir = aggregated
        pluginsClasspath = allModulesPagePluginJars
        pluginsConfigurations = pluginConfigurations(aggregatedPluginJson)
        this.modules = descriptions
    }
    DokkaGenerator(configuration, quietLogger).generate()
    return aggregated
}

/** This plugin's `pluginsConfiguration` entry holding [json], or none. */
private fun pluginConfigurations(json: String?): MutableList<PluginConfigurationImpl> = listOfNotNull(
    json?.let { PluginConfigurationImpl(KatachiDokkaPlugin::class.qualifiedName.orEmpty(), DokkaConfiguration.SerializationFormat.JSON, it) },
).toMutableList()

/** A fresh directory for one spec, deleted by the caller. */
internal fun temporaryDirectory(): File = Files.createTempDirectory("katachi-dokka").toFile()

private val quietLogger: DokkaConsoleLogger = DokkaConsoleLogger(LoggingLevel.WARN)

private val allModulesPagePluginJars: List<File> by lazy {
    val property = System.getProperty("katachi.dokka.allModulesPagePlugin")
        ?: error("Run through Gradle: tool/dokka/build.gradle.kts passes the all-modules-page plugin as a system property")
    property.split(File.pathSeparator).filter { it.isNotBlank() }.map(::File)
}

/** The kotlin-stdlib on this JVM's classpath, for Dokka to resolve `String` and friends. */
private val kotlinStdlibJar: File by lazy {
    File(Unit::class.java.protectionDomain.codeSource.location.toURI())
}

/** Writes sources in `testInline`'s format (a `/path` line before each file) under [root]. */
private fun writeInlineSources(source: String, root: File) {
    val header = Regex("^/([\\w\\-./]+\\.kt)$", RegexOption.MULTILINE)
    val headers = header.findAll(source).toList()
    headers.forEachIndexed { index, match ->
        val end = headers.getOrNull(index + 1)?.range?.first ?: source.length
        File(root, match.groupValues[1]).apply { parentFile.mkdirs() }
            .writeText(source.substring(match.range.last + 1, end).trim() + "\n")
    }
}
