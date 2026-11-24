package me.tbsten.katachi.intellij.ide.script

import com.intellij.openapi.util.SystemInfo
import com.intellij.util.EnvironmentUtil
import me.tbsten.katachi.intellij.data.script.KatachiCliLocator
import me.tbsten.katachi.intellij.ide.sdkCall
import java.nio.file.Files

/** Where the installed `katachi` is, as the probe (KATACHI-101) and the ▶ of a `*.katachi.kts` (KATACHI-100) both look for it. */
internal object KatachiCliEnvironment {
    fun locator(): KatachiCliLocator {
        // The IDE's own PATH is nearly empty when it was started from the Dock, so the login shell's is read.
        val path = sdkCall("read the PATH of the login shell") { EnvironmentUtil.getValue("PATH") }.getOrNull() ?: System.getenv("PATH")
        return KatachiCliLocator(
            pathVariable = path,
            home = System.getProperty("user.home"),
            windows = SystemInfo.isWindows,
            isExecutable = { Files.isRegularFile(it) && Files.isExecutable(it) },
        )
    }
}
