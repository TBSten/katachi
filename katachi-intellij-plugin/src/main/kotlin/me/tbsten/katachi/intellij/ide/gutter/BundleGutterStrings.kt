package me.tbsten.katachi.intellij.ide.gutter

import me.tbsten.katachi.intellij.ide.KatachiBundle
import me.tbsten.katachi.intellij.presentation.gutter.MessageGutterStrings

/**
 * The gutter's texts in the IDE: `KatachiBundle`, English by default and Japanese under the Japanese language pack.
 *
 * ```kotlin
 * val marks = GutterMarks.of(input, BundleGutterStrings)
 * ```
 */
internal object BundleGutterStrings : MessageGutterStrings() {
    // `KatachiBundle.message` takes a @PropertyKey literal, so a key passed on is checked by the tests only.
    @Suppress("HardCodedStringLiteral")
    override fun message(key: String, vararg args: Any): String = KatachiBundle.message(key, *args)
}
