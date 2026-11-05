package me.tbsten.katachi.script.internal

import java.nio.file.Path
import kotlin.io.path.isRegularFile

internal const val KIT_SCRIPT: String = "katachi-install.sh"

/**
 * The shell script a subcommand not yet written in Kotlin runs in: the copy `init` left in the
 * working directory when there is one (it records the language and the docs origin next to
 * itself), otherwise the one shipped with the command.
 */
internal fun ScriptContext.kitScript(): Path {
    findWorkdir()?.resolve("tmp/$KIT_SCRIPT")?.takeIf { it.isRegularFile() }?.let { return it }
    val shipped = home?.resolve(KIT_SCRIPT)
    if (shipped != null && shipped.isRegularFile()) return shipped
    die(
        "$KIT_SCRIPT が見つかりません。KATACHI_HOME（bin/katachi が設定します）の下か、init が作った作業用ディレクトリの tmp/ にあるはずです。",
        "Cannot find $KIT_SCRIPT. It should be under KATACHI_HOME (bin/katachi sets it) or in tmp/ of the working directory init made.",
    )
}

internal fun interface ShellRunner {
    fun run(command: List<String>, workingDirectory: Path): Int
}

internal object InheritingShellRunner : ShellRunner {
    override fun run(command: List<String>, workingDirectory: Path): Int =
        ProcessBuilder(command)
            .directory(workingDirectory.toFile())
            .inheritIO()
            .start()
            .waitFor()
}

internal fun ScriptContext.delegate(args: List<String>, runner: ShellRunner): Int =
    runner.run(listOf("sh", kitScript().toString()) + args, cwd)
