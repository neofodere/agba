package dev.neofodere.agba.emulator

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import dev.neofodere.agba.core.Keys
import dev.neofodere.agba.core.Platform
import dev.neofodere.agba.data.Settings
import kotlin.math.abs
import kotlin.math.hypot

@SuppressLint("ViewConstructor")
class ControlsView(
    context: Context,
    private val platform: Platform,
    private val settings: Settings,
) : View(context) {
    var onKeys: (Int) -> Unit = {}
    var onMenu: () -> Unit = {}
    var onRewind: (Boolean) -> Unit = {}
    var onFastForward: () -> Unit = {}
    var onSelectionChanged: (Control?) -> Unit = {}

    var fastForwardActive = false
        set(value) {
            field = value
            invalidate()
        }

    var rewindAvailable = false
        set(value) {
            field = value
            invalidate()
        }

    var editMode = false
        set(value) {
            field = value
            if (value) releaseAll()
            selected = null
            wake()
        }

    var gamepadHidden = false
        set(value) {
            if (field == value) return
            field = value
            if (value) releaseAll()
            invalidate()
        }

    var selected: Control? = null
        private set(value) {
            field = value
            onSelectionChanged(value)
        }

    private val density = resources.displayMetrics.density
    private var landscape: Boolean? = null
    private var layout = ControlLayout.defaults(platform, false)
    private var opacity = 0.6f
    private var joystick = false

    private var idleHidden = false
    private val hidden: Boolean get() = !editMode && (idleHidden || gamepadHidden)
    private val hideRunnable = Runnable {
        if (pressedKeys != 0) {
            scheduleHide()
        } else {
            idleHidden = true
            invalidate()
        }
    }

    private var stickActive = false
    private var stickDx = 0f
    private var stickDy = 0f
    private var stickPointer = -1

    private var pressedKeys = 0
    private var rewindHeld = false
    private var fastForwardHeld = false
    private var menuHeld = false
    private var dragOffsetX = 0f
    private var dragOffsetY = 0f

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val rect = RectF()
    private val path = Path()

    init {
        refreshStyle()
    }

    fun refreshStyle() {
        opacity = settings.controlsOpacity / 100f
        joystick = settings.joystick
        wake()
    }

    fun wake() {
        idleHidden = false
        scheduleHide()
        invalidate()
    }

    private fun scheduleHide() {
        removeCallbacks(hideRunnable)
        val seconds = settings.autoHideSeconds
        if (seconds > 0 && !editMode && isAttachedToWindow) postDelayed(hideRunnable, seconds * 1000L)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        scheduleHide()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(hideRunnable)
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val nowLandscape = w > h
        if (landscape == nowLandscape) return
        landscape?.let { persist(it) }
        landscape = nowLandscape
        layout = ControlLayout.fromJson(
            settings.layoutJson(ControlLayout.key(platform, nowLandscape)),
            platform,
            nowLandscape,
        )
        releaseAll()
    }

    fun persist() {
        landscape?.let { persist(it) }
    }

    private fun persist(isLandscape: Boolean) {
        settings.setLayoutJson(ControlLayout.key(platform, isLandscape), layout.toJson())
    }

    fun adjustScale(delta: Float) {
        val control = selected ?: return
        val placement = layout.placements[control] ?: return
        placement.scale = (placement.scale + delta).coerceIn(0.5f, 2.5f)
        persist()
        invalidate()
    }

    fun toggleSelectedVisibility() {
        val control = selected ?: return
        val placement = layout.placements[control] ?: return
        placement.visible = !placement.visible
        persist()
        invalidate()
    }

    fun resetLayout() {
        val isLandscape = landscape ?: return
        settings.setLayoutJson(ControlLayout.key(platform, isLandscape), null)
        layout = ControlLayout.defaults(platform, isLandscape)
        invalidate()
    }

    private fun isAvailable(control: Control): Boolean = when (control) {
        Control.L, Control.R -> platform.hasShoulders
        Control.REWIND -> rewindAvailable
        else -> true
    }

    private fun isActive(control: Control): Boolean =
        isAvailable(control) && layout.placements[control]?.visible == true

    private fun halfSize(control: Control, scale: Float): Pair<Float, Float> {
        val (w, h) = when (control) {
            Control.DPAD -> 72f to 72f
            Control.A, Control.B -> 34f to 34f
            Control.START, Control.SELECT -> 32f to 13f
            Control.L, Control.R -> 46f to 19f
            Control.MENU, Control.REWIND, Control.FAST_FORWARD -> 19f to 19f
        }
        return w * density * scale to h * density * scale
    }

    private fun isRound(control: Control) = when (control) {
        Control.A, Control.B, Control.MENU, Control.REWIND, Control.FAST_FORWARD -> true
        Control.DPAD -> joystick
        else -> false
    }

    private fun contains(control: Control, x: Float, y: Float, slack: Float): Boolean {
        val placement = layout.placements[control] ?: return false
        val cx = placement.x * width
        val cy = placement.y * height
        val (hw, hh) = halfSize(control, placement.scale)
        val extra = slack * density
        return if (isRound(control)) {
            hypot(x - cx, y - cy) <= hw + extra
        } else {
            abs(x - cx) <= hw + extra && abs(y - cy) <= hh + extra
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (editMode) return handleEdit(event)
        if (gamepadHidden) return true
        if (idleHidden) {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) wake()
            return true
        }
        if (event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_POINTER_DOWN ||
            event.actionMasked == MotionEvent.ACTION_UP
        ) {
            scheduleHide()
        }
        val action = event.actionMasked
        val finished = action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL
        if (finished) {
            stickPointer = -1
        } else if (action == MotionEvent.ACTION_POINTER_UP && event.getPointerId(event.actionIndex) == stickPointer) {
            stickPointer = -1
        } else if (joystick && stickPointer == -1 && isActive(Control.DPAD) &&
            (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN)
        ) {
            val index = event.actionIndex
            if (contains(Control.DPAD, event.getX(index), event.getY(index), STICK_SLACK)) {
                stickPointer = event.getPointerId(index)
            }
        }
        var keys = 0
        var rewind = false
        var menu = false
        var fastForward = false
        var stick = false
        var sx = 0f
        var sy = 0f
        if (!finished) {
            for (i in 0 until event.pointerCount) {
                if (action == MotionEvent.ACTION_POINTER_UP && i == event.actionIndex) continue
                val x = event.getX(i)
                val y = event.getY(i)
                if (joystick && event.getPointerId(i) == stickPointer) {
                    val p = layout.placements.getValue(Control.DPAD)
                    sx = x - p.x * width
                    sy = y - p.y * height
                    stick = true
                    keys = keys or dpadDirections(sx, sy)
                    continue
                }
                keys = keys or keysAt(x, y)
                if (isActive(Control.REWIND) && contains(Control.REWIND, x, y, 6f)) rewind = true
                if (isActive(Control.FAST_FORWARD) && contains(Control.FAST_FORWARD, x, y, 6f)) fastForward = true
                if (isActive(Control.MENU) && contains(Control.MENU, x, y, 6f)) menu = true
            }
        }
        if (stick != stickActive || sx != stickDx || sy != stickDy) {
            stickActive = stick
            stickDx = sx
            stickDy = sy
            invalidate()
        }
        update(keys, rewind, menu, fastForward)
        return true
    }

    private fun keysAt(x: Float, y: Float): Int {
        var keys = 0
        if (!joystick && isActive(Control.DPAD) && contains(Control.DPAD, x, y, 10f)) keys = keys or dpadKeys(x, y)
        if (isActive(Control.A) && contains(Control.A, x, y, 12f)) keys = keys or Keys.A
        if (isActive(Control.B) && contains(Control.B, x, y, 12f)) keys = keys or Keys.B
        if (isActive(Control.START) && contains(Control.START, x, y, 10f)) keys = keys or Keys.START
        if (isActive(Control.SELECT) && contains(Control.SELECT, x, y, 10f)) keys = keys or Keys.SELECT
        if (isActive(Control.L) && contains(Control.L, x, y, 10f)) keys = keys or Keys.L
        if (isActive(Control.R) && contains(Control.R, x, y, 10f)) keys = keys or Keys.R
        return keys
    }

    private fun dpadKeys(x: Float, y: Float): Int {
        val placement = layout.placements[Control.DPAD] ?: return 0
        return dpadDirections(x - placement.x * width, y - placement.y * height)
    }

    private fun dpadDirections(dx: Float, dy: Float): Int {
        val placement = layout.placements[Control.DPAD] ?: return 0
        val radius = hypot(dx, dy)
        val (half, _) = halfSize(Control.DPAD, placement.scale)
        if (radius < half * (if (joystick) 0.1f else 0.16f)) return 0
        val nx = dx / radius
        val ny = dy / radius
        var keys = 0
        if (nx > DIRECTION_THRESHOLD) keys = keys or Keys.RIGHT
        if (nx < -DIRECTION_THRESHOLD) keys = keys or Keys.LEFT
        if (ny > DIRECTION_THRESHOLD) keys = keys or Keys.DOWN
        if (ny < -DIRECTION_THRESHOLD) keys = keys or Keys.UP
        return keys
    }

    private fun update(keys: Int, rewind: Boolean, menu: Boolean, fastForward: Boolean) {
        if (keys != pressedKeys) {
            if (keys and pressedKeys.inv() != 0 && settings.hapticFeedback) {
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            }
            pressedKeys = keys
            onKeys(keys)
            invalidate()
        }
        if (rewind != rewindHeld) {
            rewindHeld = rewind
            onRewind(rewind)
            invalidate()
        }
        if (fastForward != fastForwardHeld) {
            fastForwardHeld = fastForward
            if (fastForward) onFastForward()
            invalidate()
        }
        if (menu != menuHeld) {
            menuHeld = menu
            if (menu) onMenu()
            invalidate()
        }
    }

    private fun releaseAll() {
        stickActive = false
        stickDx = 0f
        stickDy = 0f
        stickPointer = -1
        update(0, false, false, false)
    }

    private fun handleEdit(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val hit = Control.entries
                    .filter { isAvailable(it) && contains(it, event.x, event.y, 8f) }
                    .minByOrNull { control ->
                        val p = layout.placements.getValue(control)
                        hypot(event.x - p.x * width, event.y - p.y * height)
                    }
                selected = hit
                if (hit != null) {
                    val p = layout.placements.getValue(hit)
                    dragOffsetX = p.x * width - event.x
                    dragOffsetY = p.y * height - event.y
                }
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                val control = selected ?: return true
                val p = layout.placements.getValue(control)
                p.x = ((event.x + dragOffsetX) / width).coerceIn(0.02f, 0.98f)
                p.y = ((event.y + dragOffsetY) / height).coerceIn(0.02f, 0.98f)
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> persist()
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        if (hidden) return
        for (control in Control.entries) {
            if (!isAvailable(control)) continue
            val placement = layout.placements[control] ?: continue
            if (!placement.visible && !editMode) continue
            val dim = if (placement.visible) 1f else 0.35f
            val cx = placement.x * width
            val cy = placement.y * height
            val (hw, hh) = halfSize(control, placement.scale)
            val pressed = isPressed(control)
            fill.color = if (pressed) PRESSED_COLOR else IDLE_COLOR
            fill.alpha = ((if (pressed) 0.55f else 0.22f) * opacity * dim * 255).toInt()
            stroke.color = ACCENT_COLOR
            stroke.alpha = (opacity * dim * 255).toInt()
            label.color = ACCENT_COLOR
            label.alpha = stroke.alpha
            when (control) {
                Control.DPAD -> if (joystick) drawJoystick(canvas, cx, cy, hw) else drawDpad(canvas, cx, cy, hw)
                Control.A, Control.B -> {
                    canvas.drawCircle(cx, cy, hw, fill)
                    canvas.drawCircle(cx, cy, hw, stroke)
                    label.textSize = hw * 0.9f
                    canvas.drawText(control.label, cx, cy + label.textSize * 0.35f, label)
                }
                Control.MENU, Control.REWIND, Control.FAST_FORWARD -> drawUtility(canvas, control, cx, cy, hw)
                else -> {
                    val radius = hh * 0.5f
                    rect.set(cx - hw, cy - hh, cx + hw, cy + hh)
                    canvas.drawRoundRect(rect, radius, radius, fill)
                    canvas.drawRoundRect(rect, radius, radius, stroke)
                    label.textSize = hh * 1.0f
                    canvas.drawText(control.label.uppercase(), cx, cy + label.textSize * 0.35f, label)
                }
            }
            if (editMode && control == selected) {
                stroke.color = SELECTION_COLOR
                stroke.alpha = 255
                rect.set(cx - hw - 6 * density, cy - hh - 6 * density, cx + hw + 6 * density, cy + hh + 6 * density)
                canvas.drawRoundRect(rect, 10 * density, 10 * density, stroke)
            }
        }
    }

    private fun isPressed(control: Control): Boolean = when (control) {
        Control.A -> pressedKeys and Keys.A != 0
        Control.B -> pressedKeys and Keys.B != 0
        Control.START -> pressedKeys and Keys.START != 0
        Control.SELECT -> pressedKeys and Keys.SELECT != 0
        Control.L -> pressedKeys and Keys.L != 0
        Control.R -> pressedKeys and Keys.R != 0
        Control.REWIND -> rewindHeld
        Control.FAST_FORWARD -> fastForwardActive || fastForwardHeld
        Control.MENU -> menuHeld
        Control.DPAD -> false
    }

    private fun drawDpad(canvas: Canvas, cx: Float, cy: Float, half: Float) {
        val arm = half * 0.64f / 2f
        val corner = arm * 0.5f

        path.reset()
        path.moveTo(cx - half + corner, cy - arm)
        path.lineTo(cx - arm, cy - arm)
        path.lineTo(cx - arm, cy - half + corner)
        path.quadTo(cx - arm, cy - half, cx - arm + corner, cy - half)
        path.lineTo(cx + arm - corner, cy - half)
        path.quadTo(cx + arm, cy - half, cx + arm, cy - half + corner)
        path.lineTo(cx + arm, cy - arm)
        path.lineTo(cx + half - corner, cy - arm)
        path.quadTo(cx + half, cy - arm, cx + half, cy - arm + corner)
        path.lineTo(cx + half, cy + arm - corner)
        path.quadTo(cx + half, cy + arm, cx + half - corner, cy + arm)
        path.lineTo(cx + arm, cy + arm)
        path.lineTo(cx + arm, cy + half - corner)
        path.quadTo(cx + arm, cy + half, cx + arm - corner, cy + half)
        path.lineTo(cx - arm + corner, cy + half)
        path.quadTo(cx - arm, cy + half, cx - arm, cy + half - corner)
        path.lineTo(cx - arm, cy + arm)
        path.lineTo(cx - half + corner, cy + arm)
        path.quadTo(cx - half, cy + arm, cx - half, cy + arm - corner)
        path.lineTo(cx - half, cy - arm + corner)
        path.quadTo(cx - half, cy - arm, cx - half + corner, cy - arm)
        path.close()

        canvas.drawPath(path, fill)
        canvas.drawPath(path, stroke)

        val directions = Keys.UP or Keys.DOWN or Keys.LEFT or Keys.RIGHT
        if (pressedKeys and directions == 0) return
        fill.color = PRESSED_COLOR
        fill.alpha = (0.55f * opacity * 255).toInt()
        canvas.save()
        canvas.clipPath(path)
        if (pressedKeys and Keys.UP != 0) canvas.drawRect(cx - arm, cy - half, cx + arm, cy - arm, fill)
        if (pressedKeys and Keys.DOWN != 0) canvas.drawRect(cx - arm, cy + arm, cx + arm, cy + half, fill)
        if (pressedKeys and Keys.LEFT != 0) canvas.drawRect(cx - half, cy - arm, cx - arm, cy + arm, fill)
        if (pressedKeys and Keys.RIGHT != 0) canvas.drawRect(cx + arm, cy - arm, cx + half, cy + arm, fill)
        canvas.restore()
    }

    private fun drawJoystick(canvas: Canvas, cx: Float, cy: Float, half: Float) {
        canvas.drawCircle(cx, cy, half, fill)
        canvas.drawCircle(cx, cy, half, stroke)
        val travel = half * 0.5f
        val knob = half * 0.42f
        var kx = cx
        var ky = cy
        if (stickActive) {
            val distance = hypot(stickDx, stickDy)
            if (distance > 0f) {
                val reach = minOf(distance, travel)
                kx += stickDx / distance * reach
                ky += stickDy / distance * reach
            }
        }
        val active = stickActive && pressedKeys and (Keys.UP or Keys.DOWN or Keys.LEFT or Keys.RIGHT) != 0
        fill.color = PRESSED_COLOR
        fill.alpha = ((if (active) 0.55f else 0.35f) * opacity * 255).toInt()
        canvas.drawCircle(kx, ky, knob, fill)
        canvas.drawCircle(kx, ky, knob, stroke)
    }

    private fun drawUtility(canvas: Canvas, control: Control, cx: Float, cy: Float, radius: Float) {
        canvas.drawCircle(cx, cy, radius, fill)
        canvas.drawCircle(cx, cy, radius, stroke)
        val glyph = radius * 0.5f
        fill.color = ACCENT_COLOR
        fill.alpha = stroke.alpha
        when (control) {
            Control.MENU -> {
                val bar = radius * 0.055f
                val gap = radius * 0.26f
                val reach = radius * 0.38f
                for (offset in intArrayOf(-1, 0, 1)) {
                    rect.set(cx - reach, cy + offset * gap - bar, cx + reach, cy + offset * gap + bar)
                    canvas.drawRoundRect(rect, bar, bar, fill)
                }
            }
            Control.FAST_FORWARD -> {
                drawTriangle(canvas, cx - glyph * 0.9f, cy, glyph, true)
                drawTriangle(canvas, cx + glyph * 0.1f, cy, glyph, true)
            }
            else -> {
                drawTriangle(canvas, cx - glyph * 0.1f, cy, glyph, false)
                drawTriangle(canvas, cx + glyph * 0.9f, cy, glyph, false)
            }
        }
    }

    private fun drawTriangle(canvas: Canvas, left: Float, cy: Float, size: Float, pointsRight: Boolean) {
        path.reset()
        if (pointsRight) {
            path.moveTo(left, cy - size)
            path.lineTo(left + size, cy)
            path.lineTo(left, cy + size)
        } else {
            path.moveTo(left, cy - size)
            path.lineTo(left - size, cy)
            path.lineTo(left, cy + size)
        }
        path.close()
        canvas.drawPath(path, fill)
    }

    private companion object {
        const val DIRECTION_THRESHOLD = 0.38f
        const val STICK_SLACK = 28f
        const val IDLE_COLOR = 0xFFFFFFFF.toInt()
        const val PRESSED_COLOR = 0xFFFFFFFF.toInt()
        const val ACCENT_COLOR = 0xFFFFFFFF.toInt()
        const val SELECTION_COLOR = 0xFF7BE07B.toInt()
    }
}
