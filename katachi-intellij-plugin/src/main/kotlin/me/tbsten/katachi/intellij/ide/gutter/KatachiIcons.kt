package me.tbsten.katachi.intellij.ide.gutter

import com.intellij.openapi.util.IconLoader
import com.intellij.util.ui.EmptyIcon
import me.tbsten.katachi.intellij.ide.sdkCall
import javax.swing.Icon

/**
 * The gutter's icons. `icons/gutter/katachi.svg` is 12×12, the size of a gutter icon; the `_dark` file is picked by
 * the platform under a dark theme.
 *
 * ```kotlin
 * LineMarkerInfo(leaf, leaf.textRange, KatachiIcons.gutter, { tooltip }, handler, GutterIconRenderer.Alignment.LEFT) { name }
 * ```
 */
internal object KatachiIcons {
    val gutter: Icon by lazy {
        sdkCall("load the katachi gutter icon") { IconLoader.getIcon("/icons/gutter/katachi.svg", KatachiIcons::class.java) }
            .getOrElse { EmptyIcon.create(GUTTER_ICON_SIZE) }
    }

    private const val GUTTER_ICON_SIZE = 12
}
