package me.tbsten.katachi.intellij

import com.intellij.ide.starter.ide.IdeProductProvider
import com.intellij.ide.starter.ide.installer.ExistingIdeInstaller
import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.models.VMOptions
import java.nio.file.Path

/**
 * The IDE every real-IDE smoke starts: IntelliJ IDEA, or Android Studio under `-Dkatachi.smoke.ideProduct=AndroidStudio` (the task
 * `integrationTestAndroidStudio`). katachi ships to Android Studio too, whose Gradle sync differs from IDEA's (by default it does not
 * build the task list, which hid the definition module in KATACHI-363), so the smokes run there unchanged.
 *
 * ```kotlin
 * val bundle = workDir.resolve("ide/${SmokeProduct.appName}")
 * val ide = SmokeProduct.ideInfo { bundle }
 * Starter.newContext(name, TestCase(ide, project), false).applyVMOptionsPatch { SmokeProduct.patch(this) }
 * ```
 */
internal object SmokeProduct {
    /** Whether the smokes start Android Studio rather than IDEA. */
    val androidStudio: Boolean = System.getProperty("katachi.smoke.ideProduct") == "AndroidStudio"

    /** The name of the macOS bundle the smokes rebuild around the IDE the build resolved. */
    val appName: String = if (androidStudio) "Android Studio.app" else "IntelliJ IDEA.app"

    /** Starter's description of the IDE whose bundle [bundle] gives, read when Starter installs it. */
    fun ideInfo(bundle: () -> Path): IdeInfo = if (androidStudio) {
        // Starter knows Android Studio (product code AI, launcher `studio`) but has no ready-made IdeInfo for an existing copy.
        IdeProductProvider.IU.copy(
            productCode = "AI",
            platformPrefix = "AndroidStudio",
            executableFileName = "studio",
            fullName = "Android Studio",
            getInstaller = { ExistingIdeInstaller(bundle()) },
        )
    } else {
        IdeProductProvider.IU.copy(getInstaller = { ExistingIdeInstaller(bundle()) })
    }

    /** What every smoke adds to the IDE's VM options for this product. */
    fun patch(options: VMOptions) {
        // Android Studio's first-run wizard (SDK setup) would wait on its window for good.
        if (androidStudio) options.addSystemProperty("disable.android.first.run", true)
    }
}
