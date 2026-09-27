@file:OptIn(InternalComposeUiApi::class) // renderComposeScene

package me.tbsten.katachi.intellij.uitest

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.renderComposeScene
import me.tbsten.katachi.intellij.presentation.KatachiUiState
import me.tbsten.katachi.intellij.ui.KatachiToolWindowContent
import me.tbsten.katachi.intellij.ui.LocalStaticRendering
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File

/** A tool window size the property tests draw every state at: docked narrow, and docked wide. */
internal enum class DockSize(val width: Int, val height: Int) {
    Narrow(300, 640),
    Wide(900, 420),
}

/**
 * Draws [ui] as the tool window does, on standalone Jewel as the preview does, and returns the frame.
 * Throws whatever the composition, the measuring or the drawing throws.
 */
internal fun renderToolWindow(ui: KatachiUiState, size: DockSize, dark: Boolean = false): Image =
    renderComposeScene(width = size.width, height = size.height) {
        IntUiTheme(isDark = dark) {
            CompositionLocalProvider(LocalStaticRendering provides true) {
                Box(Modifier.fillMaxSize().background(JewelTheme.globalColors.panelBackground)) {
                    KatachiToolWindowContent(ui, onIntent = {})
                }
            }
        }
    }

internal fun Image.writePng(file: File) {
    file.parentFile.mkdirs()
    file.writeBytes(encodeToData(EncodedImageFormat.PNG)!!.bytes)
}
