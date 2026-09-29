package me.tbsten.katachi.intellij.ide.dialog

import me.tbsten.katachi.intellij.ide.KatachiBundle
import me.tbsten.katachi.intellij.ui.dialog.MessageGenerateDialogStrings

/**
 * The dialog's texts in the IDE: `KatachiBundle`, English by default and Japanese under the Japanese
 * language pack.
 *
 * ```kotlin
 * GenerateDialogContent(state, BundleGenerateDialogStrings, actions)
 * ```
 */
internal object BundleGenerateDialogStrings : MessageGenerateDialogStrings() {
    // `KatachiBundle.message` takes a @PropertyKey literal, so a key passed on is checked by the tests only.
    @Suppress("HardCodedStringLiteral")
    override fun message(key: String, vararg args: Any): String = KatachiBundle.message(key, *args)
}
