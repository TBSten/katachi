package me.tbsten.katachi.intellij

import com.intellij.ide.starter.ide.IdeProductProvider
import com.intellij.ide.starter.ide.installer.ExistingIdeInstaller
import com.intellij.ide.starter.models.IdeInfo
import com.intellij.ide.starter.models.VMOptions
import java.nio.file.Files
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
        if (!androidStudio) return
        // Android Studio's first-run wizard (SDK setup) would wait on its window for good.
        options.addSystemProperty("disable.android.first.run", true)
        // So would Google's "Help improve Android Studio" consent dialog, which a fresh config shows before any project opens
        // (Driver then times out on "Project is opened"). Read by com.android.tools.idea.stats.ConsentDialog.
        options.addSystemProperty("disable.android.analytics.consent.dialog", true)
        // And so would the "Android SDK location" dialog of the first Gradle sync (SdkSyncImpl), which a fresh config with no saved
        // SDK and a copy with no local.properties both lead to. The IDE takes ANDROID_HOME as its SDK when it is a valid one.
        options.withEnv("ANDROID_HOME", androidSdk().toString())
    }

    /**
     * The Android SDK the smokes hand to Android Studio: ANDROID_HOME, ANDROID_SDK_ROOT, then the default install locations of
     * Android Studio on macOS and Linux, the first that is a directory.
     */
    private fun androidSdk(): Path {
        val home = Path.of(System.getProperty("user.home"))
        val candidates = listOfNotNull(
            System.getenv("ANDROID_HOME")?.takeIf { it.isNotBlank() }?.let { Path.of(it) },
            System.getenv("ANDROID_SDK_ROOT")?.takeIf { it.isNotBlank() }?.let { Path.of(it) },
            home.resolve("Library/Android/sdk"),
            home.resolve("Android/Sdk"),
        )
        return candidates.firstOrNull { Files.isDirectory(it) }
            ?: error(
                "No Android SDK found for the Android Studio smokes (looked at ${candidates.joinToString()}). " +
                    "Android Studio would stop on its SDK location dialog before the project opens. " +
                    "Install an Android SDK, or set ANDROID_HOME to one.",
            )
    }
}
