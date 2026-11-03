package me.tbsten.katachi.intellij.ide.authoring

import me.tbsten.katachi.intellij.ide.KatachiBundle
import me.tbsten.katachi.intellij.presentation.authoring.MessageAuthoringStrings

internal object BundleAuthoringStrings : MessageAuthoringStrings() {
    // `KatachiBundle.message` takes a @PropertyKey literal, so a key passed on is checked by the tests only.
    @Suppress("HardCodedStringLiteral")
    override fun message(key: String, vararg args: Any): String = KatachiBundle.message(key, *args)
}
