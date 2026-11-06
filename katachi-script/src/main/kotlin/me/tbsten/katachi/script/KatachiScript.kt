package me.tbsten.katachi.script

import java.nio.file.Path
import kotlin.script.experimental.annotations.KotlinScript
import me.tbsten.katachi.script.internal.KatachiScriptCompilationConfiguration
import me.tbsten.katachi.script.internal.scriptDirectoryOf

/**
 * The base of every `*.katachi.kts` script: [katachi] and its defaults are there without any setup.
 *
 * A `*.katachi.kts` is a `.main.kts` written with [Katachi] minus the preamble: no
 * `@file:DependsOn`, no `import me.tbsten.katachi.script.*`, no `val katachi = Katachi(...)`.
 * The defaults that [Katachi]'s constructor takes are top-level variables here; [katachi] reads
 * them each time it is used, so set them before the first command.
 *
 * Run one with `katachi setup.katachi.kts`. Arguments after the file name are in [args].
 *
 * ## Example 1: lay out the test module
 *
 * ```kt
 * // setup.katachi.kts
 * defaultPackage = "com.example.app"
 *
 * executeCommands(
 *     katachi.init.lang(ja),
 *     katachi.scaffold.`test-framework`(kotest),
 * )
 * ```
 *
 * @property args The command line after the script's file name.
 * @see Katachi
 * @featured
 */
@KotlinScript(
    displayName = "katachi script",
    fileExtension = "katachi.kts",
    compilationConfiguration = KatachiScriptCompilationConfiguration::class,
)
public abstract class KatachiScript(public val args: Array<String>) {
    /** [Katachi.defaultPackage]: the `--package` every `scaffold` starts with. */
    public var defaultPackage: String? = null

    /** [Katachi.katachiVersion]: the `--katachi` every `init` and `scaffold` starts with. */
    public var katachiVersion: String? = null

    /** [Katachi.moduleDir]: the `--module-dir` every `init` and `scaffold` starts with. */
    public var moduleDir: String? = null

    /** [Katachi.lang]: the `--lang` every `init` starts with. */
    public var lang: Lang? = null

    /** [Katachi.workdir]: the `--workdir` every `init` starts with. */
    public var workdir: String? = null

    /**
     * [Katachi.cwd]: where the commands run.
     *
     * Starts as the directory of the script when it was run by `katachi <file>.katachi.kts`, so
     * the commands do not depend on where the shell was. Null is the current directory.
     */
    public var cwd: Path? = scriptDirectoryOf(System.getenv())

    /** [Katachi.runner]: how a command runs. */
    public var runner: CommandRunner = CommandRunner.InProcess

    /** [Katachi.echo]: whether a command prints `$ katachi ...` before it runs. */
    public var echo: Boolean = true

    /** The `katachi` command with the defaults above, as they are when it is read. */
    public val katachi: Katachi
        get() = Katachi(
            defaultPackage = defaultPackage,
            katachiVersion = katachiVersion,
            moduleDir = moduleDir,
            lang = lang,
            workdir = workdir,
            cwd = cwd,
            runner = runner,
            echo = echo,
        )
}
