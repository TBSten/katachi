package me.tbsten.katachi.intellij.ide.debug

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.wm.WindowManager
import com.intellij.util.ui.ImageUtil
import com.intellij.util.ui.UIUtil
import me.tbsten.katachi.intellij.ide.sdkCall
import java.awt.Component
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import javax.imageio.ImageIO

/**
 * PNGs of what the IDE draws, written by the IDE itself for the Driver smoke. The desktop screenshot Starter takes is of the
 * screen, which on a machine without screen-recording permission is the wallpaper: painting the components in the IDE needs
 * no permission and shows the frame (editor, gutter, underline, balloons) or one popup, whatever is in front of it.
 *
 * ```kotlin
 * DebugScreenshots(project).ofFrame("/tmp/out/frame.png")   // "saved=/tmp/out/frame.png|1400x900"
 * ```
 */
internal class DebugScreenshots(private val project: Project) {
    /** The IDE window of the project, written to [path]: `saved=<path>|<width>x<height>` or `saved=none`. */
    fun ofFrame(path: String): String {
        var result = "saved=none"
        ApplicationManager.getApplication().invokeAndWait({ result = save(WindowManager.getInstance().getFrame(project), path) }, ModalityState.any())
        return result
    }

    /** The content of [popup], written to [path] (call on the EDT while it is showing). */
    fun ofPopup(popup: JBPopup, path: String): String = save(popup.content, path)

    private fun save(component: Component?, path: String): String {
        if (component == null || component.width <= 0 || component.height <= 0) return "saved=none"
        return sdkCall("save a screenshot of ${component.javaClass.simpleName} to $path") {
            val image = ImageUtil.toBufferedImage(UIUtil.createImage(component, component.width, component.height, BufferedImage.TYPE_INT_RGB))
            val graphics = image.createGraphics()
            try {
                component.paint(graphics)
            } finally {
                graphics.dispose()
            }
            val target: Path = Paths.get(path)
            Files.createDirectories(target.parent)
            ImageIO.write(image, "png", target.toFile())
            "saved=$path|${image.width}x${image.height}"
        }.getOrDefault("saved=none")
    }
}
