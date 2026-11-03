package me.tbsten.katachi.intellij.ide.debug

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.wm.WindowManager
import com.intellij.util.ui.ImageUtil
import com.intellij.util.ui.UIUtil
import me.tbsten.katachi.intellij.ide.dialog.GenerateDialogs
import me.tbsten.katachi.intellij.ide.sdkCall
import java.awt.Component
import java.awt.Rectangle
import java.awt.Robot
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import javax.imageio.ImageIO

internal class DebugScreenshots(private val project: Project) {
    fun ofFrame(path: String): String {
        var result = "saved=none"
        ApplicationManager.getApplication().invokeAndWait({ result = save(WindowManager.getInstance().getFrame(project), path) }, ModalityState.any())
        return result
    }

    fun ofPopup(popup: JBPopup, path: String): String = save(popup.content, path)

    fun ofDialog(path: String): String {
        var result = "saved=none"
        ApplicationManager.getApplication().invokeAndWait({
            result = sdkCall("capture the generate dialog to $path") {
                val window = GenerateDialogs.showing?.window?.takeIf { it.isShowing } ?: return@sdkCall "saved=none"
                val bounds = Rectangle(window.locationOnScreen, window.size)
                val image = Robot().createScreenCapture(bounds)
                // Without the OS's permission the capture is one flat colour (black on macOS): that is no evidence of the dialog.
                if (image.isFlat()) "saved=none" else write(image, path)
            }.getOrDefault("saved=none")
        }, ModalityState.any())
        return result
    }

    private fun BufferedImage.isFlat(): Boolean {
        val first = getRGB(0, 0)
        return (0 until height).none { y -> (0 until width).any { x -> getRGB(x, y) != first } }
    }

    private fun write(image: BufferedImage, path: String): String {
        val target: Path = Paths.get(path)
        Files.createDirectories(target.parent)
        ImageIO.write(image, "png", target.toFile())
        return "saved=$path|${image.width}x${image.height}"
    }

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
            write(image, path)
        }.getOrDefault("saved=none")
    }
}
