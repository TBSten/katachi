package me.tbsten.katachi.intellij.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import me.tbsten.katachi.intellij.presentation.RowStatus
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.CircularProgressIndicator
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.icons.AllIconsKeys

/**
 * True in the headless preview: animated parts draw one fixed frame, so the PNGs stay
 * byte-deterministic (the spinner loads its frames on a background dispatcher).
 */
internal val LocalStaticRendering = staticCompositionLocalOf { false }

/** Faint text: defaults in empty fields, grey rows, captions. */
internal val faintText: Color
    @Composable get() = JewelTheme.globalColors.text.info

internal val errorText: Color
    @Composable get() = JewelTheme.globalColors.text.error

internal val warningText: Color
    @Composable get() = JewelTheme.globalColors.text.warning

/** Separators and the form's vertical line; the light theme's border color is too pale for a 1px line. */
internal val lineColor: Color
    @Composable get() = JewelTheme.globalColors.text.info.copy(alpha = 0.35f)

/** Module bands and banners: a tint that stands out from the panel in both themes. */
internal val bandColor: Color
    @Composable get() = JewelTheme.globalColors.text.info.copy(alpha = 0.12f)

/** A small spinner; a still ring in the preview. */
@Composable
internal fun Spinner(modifier: Modifier = Modifier) {
    if (LocalStaticRendering.current) {
        val color = faintText
        Canvas(modifier.size(16.dp)) {
            drawArc(color, startAngle = -90f, sweepAngle = 270f, useCenter = false, style = Stroke(width = 2.dp.toPx()))
        }
    } else {
        CircularProgressIndicator(modifier)
    }
}

/**
 * 🔗 drawn by hand: no platform icon shows a chain, and emoji fonts differ between machines.
 * [linked] false draws the faint broken link of an unlinked field (E-14).
 */
@Composable
internal fun LinkGlyph(linked: Boolean, modifier: Modifier = Modifier) {
    val color = if (linked) JewelTheme.globalColors.text.normal else faintText
    Canvas(modifier.size(14.dp)) {
        val stroke = Stroke(width = 1.4.dp.toPx())
        val w = size.width * 0.52f
        val h = size.height * 0.34f
        val radius = CornerRadius(h / 2, h / 2)
        val gap = if (linked) 0f else size.width * 0.12f
        rotate(-45f) {
            drawRoundRect(color, Offset(-gap, size.height / 2 - h / 2), Size(w, h), radius, style = stroke)
            drawRoundRect(color, Offset(size.width - w + gap, size.height / 2 - h / 2), Size(w, h), radius, style = stroke)
        }
    }
}

/** The icon standing in for a row's checkbox while generating and after (spec 03). */
@Composable
internal fun StatusIcon(status: RowStatus, modifier: Modifier = Modifier) {
    val box = modifier.size(16.dp)
    when (status) {
        RowStatus.Running -> Box(box, contentAlignment = Alignment.Center) { Spinner() }
        RowStatus.Waiting -> Box(box, contentAlignment = Alignment.Center) {
            val color = faintText
            Canvas(Modifier.size(4.dp)) { drawCircle(color) }
        }
        RowStatus.Done -> Icon(AllIconsKeys.RunConfigurations.TestPassed, contentDescription = null, modifier = box)
        RowStatus.Failed -> Icon(AllIconsKeys.RunConfigurations.TestFailed, contentDescription = null, modifier = box)
        RowStatus.Skipped -> Icon(AllIconsKeys.RunConfigurations.TestIgnored, contentDescription = null, modifier = box)
        RowStatus.Stopped, RowStatus.Interrupted -> Icon(AllIconsKeys.RunConfigurations.TestTerminated, contentDescription = null, modifier = box)
        RowStatus.NotRun -> Icon(AllIconsKeys.RunConfigurations.TestNotRan, contentDescription = null, modifier = box)
        RowStatus.AwaitingConflict -> Icon(AllIconsKeys.General.Warning, contentDescription = null, modifier = box)
    }
}
