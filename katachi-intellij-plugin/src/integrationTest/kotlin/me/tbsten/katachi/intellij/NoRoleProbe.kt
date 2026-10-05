package me.tbsten.katachi.intellij

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.sdk.Project
import com.intellij.driver.sdk.ui.ui

private const val NO_ROLE_PLUGIN = "me.tbsten.katachi.intellij"

/** Read-only bridge to the no-role notification: it only reports what the selected editor shows. */
@Remote("me.tbsten.katachi.intellij.ide.debug.KatachiDebugBridge", plugin = NO_ROLE_PLUGIN)
interface NoRoleBridgeRef {
    fun describeNoRole(): String

    fun screenshotFrame(out: String): String

    fun screenshotPopup(out: String): String

    fun pressNoRoleLink(text: String): String

    fun dismissNoRole(): String

    fun closePopups(): String

    fun describePopup(): String

    fun refreshFromDisk(): String
}

/** `describeNoRole()` of the running IDE, parsed into the lines the smoke compares. */
internal class NoRoleProbe(driver: Driver, project: Project) {
    private val bridge by lazy { driver.service(NoRoleBridgeRef::class, project) }

    fun describe(): String = bridge.describeNoRole()

    /** The IDE window painted by the IDE itself into [out] (Starter's desktop screenshot is the wallpaper without screen-recording permission). */
    fun screenshot(out: java.nio.file.Path) = println("screenshot " + bridge.screenshotFrame(out.toString()))

    fun screenshotPopup(out: java.nio.file.Path) = println("screenshot " + bridge.screenshotPopup(out.toString()))

    /** What a click on the link calls, done in the IDE: Driver's click moves the real mouse, which lands in the window in front, not necessarily the IDE under test. */
    fun press(link: String): String = bridge.pressNoRoleLink(link)

    fun dismiss(): String = bridge.dismissNoRole()

    fun closePopups(): String = bridge.closePopups()

    /** What focusing the IDE does after an outside `git add`; the IDE under test is not in front on a machine in use. */
    fun refreshFromDisk(): String = bridge.refreshFromDisk()

    fun popup(): List<String> = bridge.describePopup().lines()

    fun lines(): List<String> = describe().lines()

    fun value(key: String): String? = lines().firstOrNull { it.startsWith("$key=") }?.removePrefix("$key=")

    fun links(): List<String> = lines().filter { it.startsWith("link=") }.map { it.removePrefix("link=") }
}

/**
 * The no-role notification's panel. The Driver's `class` is the simple name of the component's own class, so this is `NoRolePanel`,
 * not the `KatachiNotificationPanel` it extends (that name finds the template notification, whose class it is).
 */
internal fun Driver.noRolePanel() = ui.x { byClass("NoRolePanel") }

internal fun Driver.noRoleLink(text: String) = noRolePanel().x { byVisibleText(text) }
