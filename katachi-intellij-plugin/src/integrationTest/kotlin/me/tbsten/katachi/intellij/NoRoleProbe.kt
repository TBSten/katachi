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
}

/** `describeNoRole()` of the running IDE, parsed into the lines the smoke compares. */
internal class NoRoleProbe(driver: Driver, project: Project) {
    private val bridge by lazy { driver.service(NoRoleBridgeRef::class, project) }

    fun describe(): String = bridge.describeNoRole()

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
