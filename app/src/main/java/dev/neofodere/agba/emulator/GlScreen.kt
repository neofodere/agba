package dev.neofodere.agba.emulator

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.view.View
import dev.neofodere.agba.core.NativeCore
import dev.neofodere.agba.data.ScaleMode
import dev.neofodere.agba.data.VideoFilter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.abs
import kotlin.math.max

class GlScreen(context: Context) : GameScreen {
    private val glView = GLSurfaceView(context)
    private val renderer = Renderer()

    override val view: View get() = glView
    override var scaleMode: ScaleMode
        get() = renderer.scaleMode
        set(value) { renderer.scaleMode = value }
    override var filter: VideoFilter
        get() = renderer.filter
        set(value) { renderer.filter = value }
    override var topAligned: Boolean
        get() = renderer.topAligned
        set(value) { renderer.topAligned = value }
    override var onFrameRate: (Int) -> Unit
        get() = renderer.onFrameRate
        set(value) { renderer.onFrameRate = value }

    init {
        glView.setEGLContextClientVersion(2)
        glView.setRenderer(renderer)
    }

    override fun onResume() = glView.onResume()
    override fun onPause() = glView.onPause()
    override fun snapshot(): Bitmap? = renderer.snapshot()

    private class ShaderProgram(val id: Int) {
        val position = GLES20.glGetAttribLocation(id, "aPosition")
        val uv = GLES20.glGetAttribLocation(id, "aUv")
        val texture = GLES20.glGetUniformLocation(id, "uTexture")
        val size = GLES20.glGetUniformLocation(id, "uSize")
        val scale = GLES20.glGetUniformLocation(id, "uScale")
    }

    private class Renderer : GLSurfaceView.Renderer {
        @Volatile var scaleMode = ScaleMode.FIT
        @Volatile var filter = VideoFilter.LINEAR
        @Volatile var topAligned = false
        @Volatile var onFrameRate: (Int) -> Unit = {}

        private val frame = ByteBuffer.allocateDirect(MAX_FRAME_BYTES).order(ByteOrder.nativeOrder())
        private val frameLock = Any()
        private var frameWidth = 0
        private var frameHeight = 0

        private val vertices: FloatBuffer = ByteBuffer.allocateDirect(16 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        private val programs = HashMap<VideoFilter, ShaderProgram?>()

        private var texture = 0
        private var textureWidth = 0
        private var textureHeight = 0
        private var surfaceWidth = 1
        private var surfaceHeight = 1
        private var forceUpload = true
        private var filterApplied: VideoFilter? = null
        private val counter = FrameRateCounter { onFrameRate }

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            programs.clear()
            val ids = IntArray(1)
            GLES20.glGenTextures(1, ids, 0)
            texture = ids[0]
            textureWidth = 0
            textureHeight = 0
            filterApplied = null
            forceUpload = true
            GLES20.glClearColor(0f, 0f, 0f, 1f)
            GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 4)
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            surfaceWidth = width.coerceAtLeast(1)
            surfaceHeight = height.coerceAtLeast(1)
        }

        override fun onDrawFrame(gl: GL10?) {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            if (upload()) counter.tick()
            if (frameWidth == 0 || textureWidth == 0) return

            val box = computeViewport(surfaceWidth, surfaceHeight, frameWidth, frameHeight, scaleMode, topAligned)
            GLES20.glViewport(box.x, surfaceHeight - box.y - box.height, box.width, box.height)

            val wanted = filter
            applyTextureFilter(wanted)
            val shader = program(wanted)
            GLES20.glUseProgram(shader.id)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
            GLES20.glUniform1i(shader.texture, 0)
            if (shader.size >= 0) GLES20.glUniform2f(shader.size, frameWidth.toFloat(), frameHeight.toFloat())
            if (shader.scale >= 0) {
                GLES20.glUniform2f(
                    shader.scale,
                    max(1f, box.width.toFloat() / frameWidth),
                    max(1f, box.height.toFloat() / frameHeight),
                )
            }

            val u = frameWidth.toFloat() / textureWidth
            val v = frameHeight.toFloat() / textureHeight
            vertices.clear()
            vertices.put(floatArrayOf(-1f, -1f, 0f, v, 1f, -1f, u, v, -1f, 1f, 0f, 0f, 1f, 1f, u, 0f))
            vertices.position(0)
            GLES20.glVertexAttribPointer(shader.position, 2, GLES20.GL_FLOAT, false, 16, vertices)
            GLES20.glEnableVertexAttribArray(shader.position)
            vertices.position(2)
            GLES20.glVertexAttribPointer(shader.uv, 2, GLES20.GL_FLOAT, false, 16, vertices)
            GLES20.glEnableVertexAttribArray(shader.uv)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        }

        fun snapshot(): Bitmap? = synchronized(frameLock) {
            if (frameWidth == 0) return null
            val bitmap = Bitmap.createBitmap(frameWidth, frameHeight, Bitmap.Config.ARGB_8888)
            frame.position(0)
            frame.limit(frameWidth * frameHeight * 4)
            bitmap.copyPixelsFromBuffer(frame)
            frame.clear()
            bitmap
        }

        private fun upload(): Boolean {
            val packed = synchronized(frameLock) { NativeCore.readFrame(frame, forceUpload) }
            if (packed == 0) return false
            val fresh = packed > 0
            val size = abs(packed)
            val width = (size shr 16) and 0xFFFF
            val height = size and 0xFFFF
            if (width == 0 || height == 0) return false
            synchronized(frameLock) {
                frameWidth = width
                frameHeight = height
            }
            if (!fresh && !forceUpload) return false
            forceUpload = false

            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
            frame.position(0)
            if (textureWidth != width || textureHeight != height) {
                GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, frame)
                textureWidth = width
                textureHeight = height
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
                filterApplied = null
            } else {
                GLES20.glTexSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, frame)
            }
            frame.position(0)
            return fresh
        }

        private fun applyTextureFilter(wanted: VideoFilter) {
            if (filterApplied == wanted) return
            val mode = if (wanted == VideoFilter.NEAREST) GLES20.GL_NEAREST else GLES20.GL_LINEAR
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, mode)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, mode)
            filterApplied = wanted
        }

        private fun program(filter: VideoFilter): ShaderProgram {
            val cached = programs.getOrPut(filter) { link(fragmentFor(filter)) }
            if (cached != null) return cached
            return programs.getOrPut(VideoFilter.LINEAR) { link(PLAIN_FRAGMENT) }
                ?: error("Basic shader failed to compile")
        }

        private fun fragmentFor(filter: VideoFilter) = when (filter) {
            VideoFilter.SHARP -> SHARP_FRAGMENT
            VideoFilter.FXAA -> FXAA_FRAGMENT
            else -> PLAIN_FRAGMENT
        }

        private fun link(fragmentSource: String): ShaderProgram? {
            val vertex = compile(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER) ?: return null
            val fragment = compile(GLES20.GL_FRAGMENT_SHADER, fragmentSource) ?: return null
            val id = GLES20.glCreateProgram()
            GLES20.glAttachShader(id, vertex)
            GLES20.glAttachShader(id, fragment)
            GLES20.glLinkProgram(id)
            val status = IntArray(1)
            GLES20.glGetProgramiv(id, GLES20.GL_LINK_STATUS, status, 0)
            return if (status[0] == GLES20.GL_TRUE) ShaderProgram(id) else null
        }

        private fun compile(type: Int, source: String): Int? {
            val id = GLES20.glCreateShader(type)
            GLES20.glShaderSource(id, source)
            GLES20.glCompileShader(id)
            val status = IntArray(1)
            GLES20.glGetShaderiv(id, GLES20.GL_COMPILE_STATUS, status, 0)
            return if (status[0] == GLES20.GL_TRUE) id else null
        }
    }

    private companion object {
        const val MAX_FRAME_BYTES = 256 * 256 * 4

        const val VERTEX_SHADER = """
            attribute vec2 aPosition;
            attribute vec2 aUv;
            varying vec2 vUv;
            void main() {
                gl_Position = vec4(aPosition, 0.0, 1.0);
                vUv = aUv;
            }
        """

        const val PLAIN_FRAGMENT = """
            precision mediump float;
            uniform sampler2D uTexture;
            varying vec2 vUv;
            void main() {
                gl_FragColor = vec4(texture2D(uTexture, vUv).rgb, 1.0);
            }
        """

        const val SHARP_FRAGMENT = """
            precision highp float;
            uniform sampler2D uTexture;
            uniform vec2 uSize;
            uniform vec2 uScale;
            varying vec2 vUv;
            void main() {
                vec2 texel = vUv * uSize;
                vec2 base = floor(texel);
                vec2 range = 0.5 - 0.5 / uScale;
                vec2 d = fract(texel) - 0.5;
                vec2 f = (d - clamp(d, -range, range)) * uScale + 0.5;
                gl_FragColor = vec4(texture2D(uTexture, (base + f) / uSize).rgb, 1.0);
            }
        """

        const val FXAA_FRAGMENT = """
            precision highp float;
            uniform sampler2D uTexture;
            uniform vec2 uSize;
            varying vec2 vUv;
            float luma(vec3 c) { return dot(c, vec3(0.299, 0.587, 0.114)); }
            void main() {
                vec2 px = 1.0 / uSize;
                vec3 rgbM = texture2D(uTexture, vUv).rgb;
                vec3 rgbNW = texture2D(uTexture, vUv + vec2(-1.0, -1.0) * px).rgb;
                vec3 rgbNE = texture2D(uTexture, vUv + vec2(1.0, -1.0) * px).rgb;
                vec3 rgbSW = texture2D(uTexture, vUv + vec2(-1.0, 1.0) * px).rgb;
                vec3 rgbSE = texture2D(uTexture, vUv + vec2(1.0, 1.0) * px).rgb;
                float lM = luma(rgbM);
                float lNW = luma(rgbNW);
                float lNE = luma(rgbNE);
                float lSW = luma(rgbSW);
                float lSE = luma(rgbSE);
                float lMin = min(lM, min(min(lNW, lNE), min(lSW, lSE)));
                float lMax = max(lM, max(max(lNW, lNE), max(lSW, lSE)));
                vec2 dir = vec2(-((lNW + lNE) - (lSW + lSE)), (lNW + lSW) - (lNE + lSE));
                float reduce = max((lNW + lNE + lSW + lSE) * 0.03125, 0.0078125);
                float rcp = 1.0 / (min(abs(dir.x), abs(dir.y)) + reduce);
                dir = clamp(dir * rcp, vec2(-8.0), vec2(8.0)) * px;
                vec3 rgbA = 0.5 * (
                    texture2D(uTexture, vUv + dir * (1.0 / 3.0 - 0.5)).rgb +
                    texture2D(uTexture, vUv + dir * (2.0 / 3.0 - 0.5)).rgb);
                vec3 rgbB = rgbA * 0.5 + 0.25 * (
                    texture2D(uTexture, vUv + dir * -0.5).rgb +
                    texture2D(uTexture, vUv + dir * 0.5).rgb);
                float lB = luma(rgbB);
                gl_FragColor = vec4((lB < lMin || lB > lMax) ? rgbA : rgbB, 1.0);
            }
        """
    }
}
