package dev.neofodere.agba.emulator

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.input.InputManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.MediaStore
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dev.neofodere.agba.R
import dev.neofodere.agba.app
import dev.neofodere.agba.BaseActivity
import dev.neofodere.agba.core.NativeCore
import dev.neofodere.agba.core.Keys
import dev.neofodere.agba.data.Game
import dev.neofodere.agba.data.OrientationMode
import dev.neofodere.agba.data.PadAction
import dev.neofodere.agba.data.Renderer
import dev.neofodere.agba.data.Settings
import dev.neofodere.agba.data.StateStore
import dev.neofodere.agba.settings.CheatsActivity
import dev.neofodere.agba.settings.SettingsActivity
import dev.neofodere.agba.ui.AgbaTheme
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EmulatorActivity : BaseActivity(), SensorEventListener {
    private lateinit var settings: Settings
    private lateinit var game: Game
    private lateinit var screenView: GameScreen
    private lateinit var surface: View
    private lateinit var controls: ControlsView
    private lateinit var sensors: SensorManager

    private var loaded = false
    private var destroyed = false
    private var resumed = false

    private var screen by mutableStateOf(MenuScreen.NONE)
    private var editing by mutableStateOf(false)
    private var selectedControl by mutableStateOf<Control?>(null)
    private var fastForwardLocked by mutableStateOf(false)
    private var framesPerSecond by mutableIntStateOf(0)
    private var showFps by mutableStateOf(false)
    private var slotRevision by mutableIntStateOf(0)

    private var touchKeys = 0
    private var padKeys = 0
    private var axisKeys = 0
    private var fastForwardPad = false
    private var rewindTouch = false
    private var rewindPad = false
    private var bindings: Map<Int, List<PadAction>> = emptyMap()

    private val inputListener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) = updateGamepadState()
        override fun onInputDeviceRemoved(deviceId: Int) = updateGamepadState()
        override fun onInputDeviceChanged(deviceId: Int) = updateGamepadState()
    }

    private var accelX = 0f
    private var accelY = 0f
    private var gyroZ = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = app.settings
        val found = intent.getStringExtra(EXTRA_GAME)?.let(app.library::find)
        if (found == null) {
            Toast.makeText(this, R.string.game_not_found, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        game = found
        sensors = getSystemService(SensorManager::class.java)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        buildContent()
        configureWindow()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = handleBack()
        })
        startGame()
        pollRumble()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val requested = intent.getStringExtra(EXTRA_GAME)
        if (requested != null && requested != game.id) {
            setIntent(intent)
            recreate()
        }
    }

    private fun buildContent() {
        screenView = if (settings.renderer == Renderer.SOFTWARE) SoftwareScreen(this) else GlScreen(this)
        screenView.onFrameRate = { framesPerSecond = it }
        surface = screenView.view
        controls = ControlsView(this, game.platform, settings).apply {
            onKeys = { touchKeys = it; pushKeys() }
            onMenu = { openMenu() }
            onRewind = { rewindTouch = it; updateRewind() }
            onFastForward = {
                fastForwardLocked = !fastForwardLocked
                updateFastForward()
            }
            onSelectionChanged = { selectedControl = it }
        }
        val overlay = ComposeView(this).apply {
            setContent { AgbaTheme { Overlay() } }
        }
        val root = FrameLayout(this).apply {
            setBackgroundColor(BLACK)
            addView(surface, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(controls, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(overlay, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
                screenView.topAligned = view.height > view.width
                applyScaling()
            }
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.systemBars())
            surface.layoutParams = (surface.layoutParams as FrameLayout.LayoutParams).apply {
                setMargins(cutout.left, cutout.top, cutout.right, cutout.bottom)
            }
            insets
        }
        setContentView(root)
    }

    private fun configureWindow() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun startGame() {
        lifecycleScope.launch {
            val rom = app.library.openRom(game)
            if (rom == null) {
                failAndClose(R.string.rom_open_failed)
                return@launch
            }
            val patch = app.library.openPatch(game)
            settings.pushToCore()
            val savePath = app.states.saveFile(game.id).path
            val result = withContext(NonCancellable + Dispatchers.Default) { NativeCore.load(rom, patch, savePath) }
            if (destroyed) {
                NativeCore.shutdown()
                return@launch
            }
            if (result != NativeCore.RESULT_OK) {
                failAndClose(if (result == NativeCore.RESULT_ROM_INVALID) R.string.rom_invalid else R.string.core_failed)
                return@launch
            }
            NativeCore.start()
            loaded = true
            applyCheats()
            val auto = app.states.autoFile(game.id)
            if (settings.resumeState && auto.exists()) NativeCore.loadState(auto.path)
            applyLiveSettings()
            updatePaused()
        }
    }

    private fun failAndClose(@StringRes message: Int) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        finish()
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        screenView.onResume()
        configureWindow()
        applyLiveSettings()
        registerSensors()
        getSystemService(InputManager::class.java).registerInputDeviceListener(inputListener, null)
        updateGamepadState()
        updatePaused()
    }

    override fun onPause() {
        getSystemService(InputManager::class.java).unregisterInputDeviceListener(inputListener)
        resumed = false
        releaseInputs()
        sensors.unregisterListener(this)
        screenView.onPause()
        updatePaused()
        if (loaded && settings.resumeState) NativeCore.saveState(app.states.autoFile(game.id).path)
        super.onPause()
    }

    override fun onDestroy() {
        destroyed = true
        if (loaded) {
            loaded = false
            NativeCore.shutdown()
        }
        super.onDestroy()
    }

    private fun applyLiveSettings() {
        settings.pushToCore()
        showFps = settings.showFps
        requestedOrientation = when (settings.orientation) {
            OrientationMode.AUTO -> ActivityInfo.SCREEN_ORIENTATION_FULL_USER
            OrientationMode.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
            OrientationMode.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
        }
        applyScaling()
        screenView.filter = settings.filter
        controls.visibility = if (settings.touchControls) View.VISIBLE else View.GONE
        controls.rewindAvailable = settings.rewindSeconds > 0
        controls.refreshStyle()
        updateGamepadState()
        bindings = PadAction.entries
            .filter { settings.padBinding(it) != KeyEvent.KEYCODE_UNKNOWN }
            .groupBy { settings.padBinding(it) }
        if (loaded) {
            NativeCore.setGbPalette(settings.gbPalette)
            applyCheats()
        }
    }

    private fun gamepadConnected(): Boolean = InputDevice.getDeviceIds().any { id ->
        val device = InputDevice.getDevice(id) ?: return@any false
        val sources = device.sources
        !device.isVirtual && (
            sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
                sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
            )
    }

    private fun updateGamepadState() {
        if (!::controls.isInitialized) return
        controls.gamepadHidden = settings.hideOnGamepad && gamepadConnected()
    }

    private fun applyScaling() {
        val portrait = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        screenView.scaleMode = if (portrait) settings.scalePortrait else settings.scaleLandscape
    }

    private fun applyCheats() {
        val cheats = app.cheats.load(game.id)
        NativeCore.setCheats(
            cheats.map { it.name }.toTypedArray(),
            cheats.map { it.code }.toTypedArray(),
            cheats.map { it.enabled }.toBooleanArray(),
        )
    }

    private fun updatePaused() {
        if (!loaded) return
        NativeCore.setPaused(!resumed || screen != MenuScreen.NONE || editing)
    }

    private fun pushKeys() {
        NativeCore.setKeys(touchKeys or padKeys or axisKeys)
    }

    private fun updateFastForward() {
        controls.fastForwardActive = fastForwardLocked
        NativeCore.setFastForward(fastForwardPad || fastForwardLocked)
    }

    private fun updateRewind() {
        NativeCore.setRewinding(rewindTouch || rewindPad)
    }

    private fun releaseInputs() {
        touchKeys = 0
        padKeys = 0
        axisKeys = 0
        fastForwardPad = false
        rewindTouch = false
        rewindPad = false
        if (loaded) {
            pushKeys()
            updateFastForward()
            updateRewind()
        }
    }

    private fun openMenu() {
        padKeys = 0
        axisKeys = 0
        if (loaded) pushKeys()
        screen = MenuScreen.MAIN
        updatePaused()
    }

    private fun closeMenu() {
        screen = MenuScreen.NONE
        updatePaused()
    }

    private fun handleBack() {
        when {
            editing -> stopEditing()
            screen == MenuScreen.MAIN -> closeMenu()
            screen != MenuScreen.NONE -> screen = MenuScreen.MAIN
            else -> openMenu()
        }
    }

    private fun startEditing() {
        closeMenu()
        editing = true
        controls.editMode = true
        updatePaused()
    }

    private fun stopEditing() {
        controls.editMode = false
        controls.persist()
        editing = false
        updatePaused()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!loaded || screen != MenuScreen.NONE || editing) return super.dispatchKeyEvent(event)
        val actions = bindings[event.keyCode] ?: return super.dispatchKeyEvent(event)
        val inputSources = InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_DPAD or InputDevice.SOURCE_KEYBOARD
        if (event.source and inputSources == 0) return super.dispatchKeyEvent(event)
        if (event.repeatCount == 0) {
            val down = event.action == KeyEvent.ACTION_DOWN
            actions.forEach { handlePad(it, down) }
        }
        return true
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        val joystick = event.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
        if (!loaded || screen != MenuScreen.NONE || editing || !joystick || event.action != MotionEvent.ACTION_MOVE) {
            return super.dispatchGenericMotionEvent(event)
        }
        val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
        val x = if (abs(hatX) > AXIS_THRESHOLD) hatX else event.getAxisValue(MotionEvent.AXIS_X)
        val y = if (abs(hatY) > AXIS_THRESHOLD) hatY else event.getAxisValue(MotionEvent.AXIS_Y)
        var keys = 0
        if (x > AXIS_THRESHOLD) keys = keys or Keys.RIGHT
        if (x < -AXIS_THRESHOLD) keys = keys or Keys.LEFT
        if (y > AXIS_THRESHOLD) keys = keys or Keys.DOWN
        if (y < -AXIS_THRESHOLD) keys = keys or Keys.UP
        if (keys != axisKeys) {
            axisKeys = keys
            pushKeys()
        }
        return true
    }

    private fun handlePad(action: PadAction, down: Boolean) {
        when (action) {
            PadAction.FAST_FORWARD -> {
                fastForwardPad = down
                updateFastForward()
            }
            PadAction.REWIND -> {
                rewindPad = down
                updateRewind()
            }
            PadAction.MENU -> if (down) openMenu()
            PadAction.QUICK_SAVE -> if (down) quickSave()
            PadAction.QUICK_LOAD -> if (down) quickLoad()
            else -> {
                padKeys = if (down) padKeys or action.gameKey else padKeys and action.gameKey.inv()
                pushKeys()
            }
        }
    }

    private fun registerSensors() {
        sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        sensors.getDefaultSensor(Sensor.TYPE_GYROSCOPE)?.let {
            sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        val light = sensors.getDefaultSensor(Sensor.TYPE_LIGHT)
        if (settings.lightSensor && light != null) {
            sensors.registerListener(this, light, SensorManager.SENSOR_DELAY_NORMAL)
        } else if (loaded) {
            NativeCore.setLuxIndex(settings.solarLevel)
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!loaded) return
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                val x = event.values[0]
                val y = event.values[1]
                when (displayRotation()) {
                    Surface.ROTATION_90 -> { accelX = -y; accelY = x }
                    Surface.ROTATION_180 -> { accelX = -x; accelY = -y }
                    Surface.ROTATION_270 -> { accelX = y; accelY = -x }
                    else -> { accelX = x; accelY = y }
                }
                NativeCore.setSensors(accelX, accelY, gyroZ)
            }
            Sensor.TYPE_GYROSCOPE -> {
                gyroZ = event.values[2]
                NativeCore.setSensors(accelX, accelY, gyroZ)
            }
            Sensor.TYPE_LIGHT -> NativeCore.setLuxSensor(event.values[0], true)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun displayRotation(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display?.rotation ?: Surface.ROTATION_0
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.rotation
        }

    private fun pollRumble() {
        val vibrator = getSystemService(Vibrator::class.java) ?: return
        if (!vibrator.hasVibrator()) return
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    delay(RUMBLE_POLL_MS)
                    if (!loaded || !settings.rumble || screen != MenuScreen.NONE) continue
                    val level = NativeCore.rumbleLevel()
                    if (level > RUMBLE_THRESHOLD) {
                        val amplitude = (level * 255).toInt().coerceIn(1, 255)
                        vibrator.vibrate(VibrationEffect.createOneShot(RUMBLE_PULSE_MS, amplitude))
                    }
                }
            }
        }
    }

    private fun slotFile(slot: Int): File = app.states.slotFile(game.id, slot)

    private fun saveSlot(slot: Int) {
        if (!NativeCore.saveState(slotFile(slot).path)) {
            Toast.makeText(this, R.string.state_save_failed, Toast.LENGTH_SHORT).show()
            return
        }
        screenView.snapshot()?.let { writePng(it, app.states.thumbnailFile(game.id, slot)) }
        slotRevision++
        Toast.makeText(this, R.string.state_saved, Toast.LENGTH_SHORT).show()
    }

    private fun loadSlot(slot: Int): Boolean {
        val file = slotFile(slot)
        val ok = file.exists() && NativeCore.loadState(file.path)
        Toast.makeText(this, if (ok) R.string.state_loaded else R.string.state_load_failed, Toast.LENGTH_SHORT).show()
        return ok
    }

    private fun quickSave() {
        saveSlot(QUICK_SLOT)
    }

    private fun quickLoad() {
        loadSlot(QUICK_SLOT)
    }

    private fun writePng(bitmap: Bitmap, file: File) {
        runCatching { FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
    }

    private fun takeScreenshot() {
        val snapshot = screenView.snapshot()
        if (snapshot == null) {
            Toast.makeText(this, R.string.no_frame, Toast.LENGTH_SHORT).show()
            return
        }
        val scaled = Bitmap.createScaledBitmap(snapshot, snapshot.width * SCREENSHOT_SCALE, snapshot.height * SCREENSHOT_SCALE, false)
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val name = "${game.title.replace(Regex("[^A-Za-z0-9._-]"), "_")}_$stamp.png"
        val saved = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, name)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/aGBA")
                }
                val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)!!
                contentResolver.openOutputStream(uri)!!.use { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }
            } else {
                val dir = File(getExternalFilesDir(null) ?: filesDir, "agba/screenshots").apply { mkdirs() }
                FileOutputStream(File(dir, name)).use { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }.isSuccess
        Toast.makeText(this, if (saved) R.string.screenshot_saved else R.string.screenshot_failed, Toast.LENGTH_SHORT).show()
    }

    private fun buildSlots(): List<SlotInfo> = (1..StateStore.SLOT_COUNT).map { slot ->
        val file = slotFile(slot)
        SlotInfo(
            slot = slot,
            modified = if (file.exists()) file.lastModified() else null,
            thumbnail = app.states.thumbnailFile(game.id, slot).takeIf { it.exists() }?.let { decodeThumbnail(it.path) },
        )
    }

    private fun resetGame() {
        NativeCore.reset()
        closeMenu()
    }

    private fun menuActions() = MenuActions(
        onResume = ::closeMenu,
        onShowSlots = { screen = it },
        onBackToMain = { screen = MenuScreen.MAIN },
        onSlot = { target, slot ->
            if (target == MenuScreen.SAVE) {
                saveSlot(slot)
                closeMenu()
            } else if (loadSlot(slot)) {
                closeMenu()
            }
        },
        onQuickSave = {
            quickSave()
            closeMenu()
        },
        onQuickLoad = {
            if (loadSlot(QUICK_SLOT)) closeMenu()
        },
        onReset = ::resetGame,
        onToggleFastForward = {
            fastForwardLocked = !fastForwardLocked
            updateFastForward()
        },
        onScreenshot = {
            takeScreenshot()
            closeMenu()
        },
        onCheats = { startActivity(CheatsActivity.intentFor(this, game.id)) },
        onSettings = { startActivity(Intent(this, SettingsActivity::class.java)) },
        onEditControls = ::startEditing,
        onQuit = ::finish,
    )

    @Composable
    private fun Overlay() {
        Box(modifier = Modifier.fillMaxSize()) {
            if (showFps && screen == MenuScreen.NONE) {
                Text(
                    text = "$framesPerSecond FPS",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .statusBarsPadding()
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            if (screen != MenuScreen.NONE) {
                val slots = remember(screen, slotRevision) { buildSlots() }
                val actions = remember { menuActions() }
                val cover by produceState<ImageBitmap?>(null) {
                    value = withContext(Dispatchers.IO) { app.library.loadCover(game.id, COVER_SIZE)?.asImageBitmap() }
                }
                PauseMenu(screen, game.title, game.platform, cover, fastForwardLocked, slots, actions)
            }
            if (editing) {
                EditBar(
                    selected = selectedControl,
                    onSmaller = { controls.adjustScale(-SCALE_STEP) },
                    onLarger = { controls.adjustScale(SCALE_STEP) },
                    onToggleVisible = controls::toggleSelectedVisibility,
                    onReset = controls::resetLayout,
                    onDone = ::stopEditing,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }

    companion object {
        private const val EXTRA_GAME = "dev.neofodere.agba.GAME"
        private const val ACTION_PLAY = "dev.neofodere.agba.PLAY"
        private const val BLACK = 0xFF000000.toInt()
        private const val QUICK_SLOT = 0
        private const val AXIS_THRESHOLD = 0.5f
        private const val RUMBLE_POLL_MS = 40L
        private const val RUMBLE_PULSE_MS = 60L
        private const val RUMBLE_THRESHOLD = 0.05f
        private const val SCREENSHOT_SCALE = 4
        private const val SCALE_STEP = 0.1f
        private const val COVER_SIZE = 160

        fun intentFor(context: Context, gameId: String): Intent =
            Intent(context, EmulatorActivity::class.java)
                .setAction(ACTION_PLAY)
                .putExtra(EXTRA_GAME, gameId)
    }
}
