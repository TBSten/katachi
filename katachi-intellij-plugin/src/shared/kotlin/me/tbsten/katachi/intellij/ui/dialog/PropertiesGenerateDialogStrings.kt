package me.tbsten.katachi.intellij.ui.dialog

import java.io.File
import java.text.MessageFormat
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
    override fun message(key: String, vararg args: Any): String {
        val pattern = properties.getProperty(key) ?: error("KatachiBundle has no key `$key`")
        return if (args.isEmpty()) pattern else MessageFormat(pattern).format(args)
    }

    companion object {
        /** The properties files of the plugin, relative to the module directory (the working directory of Gradle tests). */
        const val MESSAGES_DIR = "src/main/resources/messages"

        /** The English texts (`KatachiBundle.properties`, the language-less file). */
        fun english(dir: File = File(MESSAGES_DIR)) = PropertiesGenerateDialogStrings(load(File(dir, "KatachiBundle.properties")))

        /** The Japanese texts (`KatachiBundle_ja.properties`). */
        fun japanese(dir: File = File(MESSAGES_DIR)) = PropertiesGenerateDialogStrings(load(File(dir, "KatachiBundle_ja.properties")))

        /** Reads [file] as UTF-8 (`Properties.load(InputStream)` would read it as Latin-1). */
        fun load(file: File): Properties = Properties().also { p -> file.bufferedReader(Charsets.UTF_8).use(p::load) }
    }
}
