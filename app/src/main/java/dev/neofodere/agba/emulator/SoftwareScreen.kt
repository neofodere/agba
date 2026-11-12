package dev.neofodere.agba.emulator

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import dev.neofodere.agba.core.NativeCore
import dev.neofodere.agba.data.ScaleMode
import dev.neofodere.agba.data.VideoFilter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

class SoftwareScreen(context: Context) : GameScreen, SurfaceHolder.Callback {
    private val surfaceView = SurfaceView(context)

    @Volatile override var scaleMode = ScaleMode.FIT
    @Volatile override var filter = VideoFilter.LINEAR
    @Volatile override var topAligned = false
    @Volatile override var onFrameRate: (Int) -> Unit = {}

    override val view: View get() = surfaceView

    private val frame = ByteBuffer.allocateDirect(MAX_FRAME_BYTES).order(ByteOrder.nativeOrder())
    private val frameLock = Any()
    private val paint = Paint()
    private val source = Rect()
    private val target = Rect()
    private val counter = FrameRateCounter { onFrameRate }

    private var bitmap: Bitmap? = null
    private var frameWidth = 0
    private var frameHeight = 0
    private var surfaceReady = false
    private var visible = false
    private var worker: Thread? = null

    init {
        surfaceView.holder.addCallback(this)
    }

    override fun onResume() {
        visible = true
        startWorker()
    }

    override fun onPause() {
        visible = false
        stopWorker()
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        surfaceReady = true
        startWorker()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        surfaceReady = false
        stopWorker()
    }

    override fun snapshot(): Bitmap? = synchronized(frameLock) {
        val current = bitmap ?: return null
        current.copy(Bitmap.Config.ARGB_8888, false)
    }

    @Synchronized
    private fun startWorker() {
        if (!visible || !surfaceReady || worker != null) return
        worker = Thread(::drawLoop, "agba-software-screen").also { it.start() }
    }

    @Synchronized
    private fun stopWorker() {
        val thread = worker ?: return
        worker = null
        thread.interrupt()
        runCatching { thread.join(JOIN_TIMEOUT_MS) }
    }

    private fun drawLoop() {
        var force = true
        while (!Thread.currentThread().isInterrupted) {
            val fresh = pullFrame(force)
            if (fresh || force) {
                force = !draw()
            }
            try {
                Thread.sleep(POLL_MS)
            } catch (_: InterruptedException) {
                return
            }
        }
    }

    private fun pullFrame(force: Boolean): Boolean {
        val packed = NativeCore.readFrame(frame, force)
        if (packed == 0) return false
        val size = abs(packed)
        val width = (size shr 16) and 0xFFFF
        val height = size and 0xFFFF
        if (width == 0 || height == 0) return false
        if (packed < 0 && !force) return false
        synchronized(frameLock) {
            var target = bitmap
            if (target == null || target.width != width || target.height != height) {
                target = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bitmap = target
            }
            frame.position(0)
            frame.limit(width * height * 4)
            target!!.copyPixelsFromBuffer(frame)
            frame.clear()
            frameWidth = width
            frameHeight = height
        }
        counter.tick()
        return true
    }

    private fun draw(): Boolean {
        val holder = surfaceView.holder
        val canvas = runCatching { holder.lockCanvas() }.getOrNull() ?: return false
        try {
            canvas.drawColor(Color.BLACK)
            val current = synchronized(frameLock) { bitmap } ?: return true
            val box = computeViewport(canvas.width, canvas.height, frameWidth, frameHeight, scaleMode, topAligned)
            source.set(0, 0, frameWidth, frameHeight)
            target.set(box.x, box.y, box.x + box.width, box.y + box.height)
            paint.isFilterBitmap = filter != VideoFilter.NEAREST
            canvas.drawBitmap(current, source, target, paint)
        } finally {
            holder.unlockCanvasAndPost(canvas)
        }
        return true
    }

    private companion object {
        const val MAX_FRAME_BYTES = 256 * 256 * 4
        const val POLL_MS = 4L
        const val JOIN_TIMEOUT_MS = 500L
    }
}
