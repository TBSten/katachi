package me.tbsten.katachi.intellij.presentation

import java.io.File
import java.text.MessageFormat
import java.util.Properties

/**
 * The texts of a `KatachiBundle*.properties` file read without the IDE (no `DynamicBundle`), for the tests and the preview.
 * A text is formatted the way the IDE's bundle formats it: as it is without arguments, through [MessageFormat] with them.
 *
 * ```kotlin
 * val japanese = PropertiesMessages.japanese()
 * PropertiesMessages.message(japanese, "gutter.menu.runCheck", ":arch:test")
 * ```
 */
internal object PropertiesMessages {
    /** The properties files of the plugin, relative to the module directory (the working directory of Gradle tests). */
    const val MESSAGES_DIR = "src/main/resources/messages"

    /** The English texts (`KatachiBundle.properties`, the language-less file). */
    fun english(dir: File = File(MESSAGES_DIR)): Properties = load(File(dir, "KatachiBundle.properties"))

    /** The Japanese texts (`KatachiBundle_ja.properties`). */
    fun japanese(dir: File = File(MESSAGES_DIR)): Properties = load(File(dir, "KatachiBundle_ja.properties"))

    /** Reads [file] as UTF-8 (`Properties.load(InputStream)` would read it as Latin-1). */
    fun load(file: File): Properties = Properties().also { p -> file.bufferedReader(Charsets.UTF_8).use(p::load) }

    /** The text of [key] in [properties] with `{0}`, `{1}` ... replaced by [args]. */
    fun message(properties: Properties, key: String, vararg args: Any): String {
        val pattern = properties.getProperty(key) ?: error("KatachiBundle has no key `$key`")
        return if (args.isEmpty()) pattern else MessageFormat(pattern).format(args)
    }
}
