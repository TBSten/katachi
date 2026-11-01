package me.tbsten.katachi.intellij.presentation.authoring

import me.tbsten.katachi.intellij.presentation.PropertiesMessages
import java.io.File
import java.util.Properties

/**
 * [AuthoringStrings] read straight from a `KatachiBundle*.properties` file, for the tests, which run the pure models without the IDE.
 *
 * ```kotlin
 * val strings = PropertiesAuthoringStrings.japanese()
 * ```
 */
internal class PropertiesAuthoringStrings(private val properties: Properties) : MessageAuthoringStrings() {
    override fun message(key: String, vararg args: Any): String = PropertiesMessages.message(properties, key, *args)

    companion object {
        fun english(dir: File = File(PropertiesMessages.MESSAGES_DIR)) = PropertiesAuthoringStrings(PropertiesMessages.english(dir))

        fun japanese(dir: File = File(PropertiesMessages.MESSAGES_DIR)) = PropertiesAuthoringStrings(PropertiesMessages.japanese(dir))
    }
}
