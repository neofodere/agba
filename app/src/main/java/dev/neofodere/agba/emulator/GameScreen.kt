package dev.neofodere.agba.emulator

import android.graphics.Bitmap
import android.view.View
import dev.neofodere.agba.data.ScaleMode
import dev.neofodere.agba.data.VideoFilter
import kotlin.math.floor
import kotlin.math.min

interface GameScreen {
    val view: View
    var scaleMode: ScaleMode
    var filter: VideoFilter
    var topAligned: Boolean
    var onFrameRate: (Int) -> Unit

    fun onResume()
    fun onPause()
    fun snapshot(): Bitmap?
}

class Viewport(val x: Int, val y: Int, val width: Int, val height: Int)

fun computeViewport(
    surfaceWidth: Int,
    surfaceHeight: Int,
    frameWidth: Int,
    frameHeight: Int,
    mode: ScaleMode,
    topAligned: Boolean,
): Viewport {
    var width = surfaceWidth
    var height = surfaceHeight
    if (mode != ScaleMode.STRETCH) {
        var scale = min(surfaceWidth.toFloat() / frameWidth, surfaceHeight.toFloat() / frameHeight)
        if (mode == ScaleMode.INTEGER && scale >= 1f) scale = floor(scale)
        width = (frameWidth * scale).toInt().coerceAtLeast(1)
        height = (frameHeight * scale).toInt().coerceAtLeast(1)
    }
    val x = (surfaceWidth - width) / 2
    val y = if (topAligned) 0 else (surfaceHeight - height) / 2
    return Viewport(x, y, width, height)
}

class FrameRateCounter(private val report: () -> (Int) -> Unit) {
    private var frames = 0
    private var start = 0L

    fun tick() {
        val now = System.nanoTime()
        if (start == 0L) start = now
        frames++
        if (now - start >= 1_000_000_000L) {
            report()(frames)
            frames = 0
            start = now
        }
    }
}
