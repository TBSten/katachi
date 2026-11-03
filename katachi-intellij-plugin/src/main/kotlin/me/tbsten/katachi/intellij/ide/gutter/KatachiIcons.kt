package me.tbsten.katachi.intellij.ide.gutter

import com.intellij.openapi.util.IconLoader
import com.intellij.util.ui.EmptyIcon
import me.tbsten.katachi.intellij.ide.sdkCall
import javax.swing.Icon

// 12x12 is the gutter icon size; the platform picks the `_dark` file under a dark theme.
internal object KatachiIcons {
    val gutter: Icon by lazy {
        sdkCall("load the katachi gutter icon") { IconLoader.getIcon("/icons/gutter/katachi.svg", KatachiIcons::class.java) }
            .getOrElse { EmptyIcon.create(GUTTER_ICON_SIZE) }
    }

    private const val GUTTER_ICON_SIZE = 12
}
