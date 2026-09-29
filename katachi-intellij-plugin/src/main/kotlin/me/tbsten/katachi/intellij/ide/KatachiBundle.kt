package me.tbsten.katachi.intellij.ide

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey

private const val BUNDLE = "messages.KatachiBundle"

/**
 * The IDE side's texts (`messages/KatachiBundle.properties`, Japanese in `_ja`): the conflict
 * dialog, notifications, actions and settings.
 *
 * ```kotlin
 * val title = KatachiBundle.message("conflict.title", 2, 3, "Repository")
 * ```
 */
internal object KatachiBundle : DynamicBundle(KatachiBundle::class.java, BUNDLE) {
    @Nls
    fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): String = getMessage(key, *params)
}
