package me.tbsten.katachi.script.internal

import java.nio.file.Path
import kotlin.io.path.Path

// What differs between Windows and the POSIX hosts katachi first ran on. Each place that depends on the host asks here (or
// takes `windows` from [ScriptContext]) so that a test can answer for Windows on any host.

/** `true` on Windows. */
internal fun isWindowsHost(): Boolean = System.getProperty("os.name").orEmpty().startsWith("Windows")

/** How a person starts Gradle in the project: `gradlew.bat` is what a Windows shell finds (`./gradlew` is not a cmd.exe path). */
internal val GRADLEW: String get() = if (isWindowsHost()) ".\\gradlew.bat" else "./gradlew"

/** The separator of PATH entries. */
internal fun pathSeparatorOf(windows: Boolean): Char = if (windows) ';' else ':'

/** The `java` of the JDK at [home]: `bin/java`, or `bin\java.exe` on Windows. */
internal fun javaExecutableIn(home: String, windows: Boolean): Path = Path(home, "bin", if (windows) "java.exe" else "java")

/** The names [program] may be started as: itself on POSIX; with each extension of PATHEXT on Windows (`java` is `java.exe`). */
internal fun executableNames(program: String, windows: Boolean, pathExt: String?): List<String> {
    if (!windows) return listOf(program)
    val extensions = (pathExt?.takeIf { it.isNotBlank() } ?: ".COM;.EXE;.BAT;.CMD").split(';').filter { it.isNotEmpty() }
    // Already `kotlin.bat`, `katachi.cmd`: take it as it is.
    if (extensions.any { program.endsWith(it, ignoreCase = true) }) return listOf(program)
    return extensions.map { program + it.lowercase() } + program
}
