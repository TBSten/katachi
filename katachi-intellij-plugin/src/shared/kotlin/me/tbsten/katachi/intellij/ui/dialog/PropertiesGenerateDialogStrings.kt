package me.tbsten.katachi.intellij.ui.dialog

import me.tbsten.katachi.intellij.presentation.PropertiesMessages
import java.io.File
import java.util.Properties

/**
 * [GenerateDialogStrings] read straight from a `KatachiBundle*.properties` file, for the tests and the
 * preview, which run without the IDE (no `DynamicBundle`) and so without touching the build.
 *
 * ```kotlin
 * val strings = PropertiesGenerateDialogStrings.english()
 * ```
 */
internal class PropertiesGenerateDialogStrings(private val properties: Properties) : MessageGenerateDialogStrings() {
    override fun message(key: String, vararg args: Any): String = PropertiesMessages.message(properties, key, *args)

    companion object {
        /** The properties files of the plugin, relative to the module directory (the working directory of Gradle tests). */
        const val MESSAGES_DIR = PropertiesMessages.MESSAGES_DIR

        /** The English texts (`KatachiBundle.properties`, the language-less file). */
        fun english(dir: File = File(MESSAGES_DIR)) = PropertiesGenerateDialogStrings(PropertiesMessages.english(dir))

        /** The Japanese texts (`KatachiBundle_ja.properties`). */
        fun japanese(dir: File = File(MESSAGES_DIR)) = PropertiesGenerateDialogStrings(PropertiesMessages.japanese(dir))

        /** Reads [file] as UTF-8 (`Properties.load(InputStream)` would read it as Latin-1). */
        fun load(file: File): Properties = PropertiesMessages.load(file)
    }
}
