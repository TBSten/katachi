package me.tbsten.katachi.intellij.ide.authoring

import me.tbsten.katachi.intellij.ide.KatachiBundle
import me.tbsten.katachi.intellij.presentation.authoring.MessageAuthoringStrings

/**
 * The definition file's texts in the IDE: `KatachiBundle`, English by default and Japanese under the Japanese language pack.
 *
 * ```kotlin
 * val text = CountText.of(entries, stale, BundleAuthoringStrings, modules)
 * ```
 */
internal object BundleAuthoringStrings : MessageAuthoringStrings() {
    // `KatachiBundle.message` takes a @PropertyKey literal, so a key passed on is checked by the tests only.
    @Suppress("HardCodedStringLiteral")
    override fun message(key: String, vararg args: Any): String = KatachiBundle.message(key, *args)
}
