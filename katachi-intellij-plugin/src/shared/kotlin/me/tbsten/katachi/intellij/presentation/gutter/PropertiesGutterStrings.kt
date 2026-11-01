package me.tbsten.katachi.intellij.presentation.gutter

import me.tbsten.katachi.intellij.presentation.PropertiesMessages
import java.io.File
import java.util.Properties

/**
 * [GutterStrings] read straight from a `KatachiBundle*.properties` file, for the tests, which run the pure models without the IDE.
 *
 * ```kotlin
 * val strings = PropertiesGutterStrings.japanese()
 * ```
 */
internal class PropertiesGutterStrings(private val properties: Properties) : MessageGutterStrings() {
    override fun message(key: String, vararg args: Any): String = PropertiesMessages.message(properties, key, *args)

    companion object {
        fun english(dir: File = File(PropertiesMessages.MESSAGES_DIR)) = PropertiesGutterStrings(PropertiesMessages.english(dir))

        fun japanese(dir: File = File(PropertiesMessages.MESSAGES_DIR)) = PropertiesGutterStrings(PropertiesMessages.japanese(dir))
    }
}
