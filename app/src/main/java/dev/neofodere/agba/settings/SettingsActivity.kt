package dev.neofodere.agba.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.neofodere.agba.BaseActivity
import dev.neofodere.agba.R
import dev.neofodere.agba.app
import dev.neofodere.agba.core.NativeCore
import dev.neofodere.agba.core.Platform
import dev.neofodere.agba.data.AppLanguage
import dev.neofodere.agba.data.OrientationMode
import dev.neofodere.agba.data.PadAction
import dev.neofodere.agba.data.Renderer
import dev.neofodere.agba.data.ScaleMode
import dev.neofodere.agba.data.Settings
import dev.neofodere.agba.data.ThemeMode
import dev.neofodere.agba.data.VideoFilter
import dev.neofodere.agba.ui.AgbaHeader
import dev.neofodere.agba.ui.AgbaTheme
import java.io.File

class SettingsActivity : BaseActivity() {
    private lateinit var settings: Settings
    private var directoryTarget: Platform? = null
    private var biosTarget: String? = null
    private var captureTarget by mutableStateOf<PadAction?>(null)
    private var revision by mutableIntStateOf(0)

    private val directoryPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val platform = directoryTarget
        if (uri != null && platform != null) {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            settings.setRomDirectory(platform, uri.toString())
            revision++
        }
        directoryTarget = null
    }

    private val biosPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val key = biosTarget
        if (uri != null && key != null) importBios(key, uri)
        biosTarget = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = app.settings
        setContent {
            AgbaTheme {
                SettingsScreen(
                    settings = settings,
                    revision = revision,
                    versionName = versionName(),
                    initialPage = intent.getStringExtra(EXTRA_PAGE)?.let { runCatching { Page.valueOf(it) }.getOrNull() },
                    capturing = captureTarget,
                    onPickDirectory = {
                        directoryTarget = it
                        directoryPicker.launch(null)
                    },
                    onClearDirectory = {
                        settings.setRomDirectory(it, null)
                        revision++
                    },
                    onPickBios = {
                        biosTarget = it
                        biosPicker.launch(arrayOf("*/*"))
                    },
                    onClearBios = {
                        settings.biosPath(it)?.let { path -> File(path).delete() }
                        settings.setBiosPath(it, null)
                        revision++
                    },
                    onCapture = { captureTarget = it },
                    onLanguageChanged = ::recreate,
                    onBack = ::finish,
                )
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val action = captureTarget ?: return super.dispatchKeyEvent(event)
        if (event.action == KeyEvent.ACTION_DOWN && event.keyCode != KeyEvent.KEYCODE_BACK) {
            settings.setPadBinding(action, event.keyCode)
            captureTarget = null
            revision++
        }
        return event.keyCode != KeyEvent.KEYCODE_BACK || super.dispatchKeyEvent(event)
    }

    private fun importBios(key: String, uri: Uri) {
        val target = File(File(filesDir, "bios").apply { mkdirs() }, "$key.bin")
        val copied = runCatching {
            contentResolver.openInputStream(uri)!!.use { input -> target.outputStream().use { input.copyTo(it) } }
        }.isSuccess
        if (copied) {
            settings.setBiosPath(key, target.path)
            revision++
        }
    }

    companion object {
        private const val EXTRA_PAGE = "dev.neofodere.agba.PAGE"

        fun intentFor(context: Context, page: Page? = null): Intent =
            Intent(context, SettingsActivity::class.java).apply { page?.let { putExtra(EXTRA_PAGE, it.name) } }
    }

    private fun versionName(): String {
        val name = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull().orEmpty()
        return "v$name (${getString(R.string.build_number)})"
    }
}

enum class Page(@StringRes val title: Int, @StringRes val summary: Int) {
    APPEARANCE(R.string.appearance, R.string.appearance_summary),
    ROM_FOLDERS(R.string.rom_folders, R.string.rom_summary),
    VIDEO(R.string.video, R.string.video_summary),
    AUDIO(R.string.audio, R.string.audio_summary),
    EMULATION(R.string.emulation, R.string.emulation_summary),
    BIOS(R.string.bios, R.string.bios_summary),
    TOUCH(R.string.touch_controls, R.string.touch_summary),
    GAMEPAD(R.string.gamepad_sensors, R.string.gamepad_summary),
}

private val BIOS_FILES = listOf(
    "gba" to R.string.bios_gba,
    "gb" to R.string.bios_gb,
    "gbc" to R.string.bios_gbc,
)

@Composable
private fun SettingsScreen(
    settings: Settings,
    revision: Int,
    versionName: String,
    initialPage: Page?,
    capturing: PadAction?,
    onPickDirectory: (Platform) -> Unit,
    onClearDirectory: (Platform) -> Unit,
    onPickBios: (String) -> Unit,
    onClearBios: (String) -> Unit,
    onCapture: (PadAction?) -> Unit,
    onLanguageChanged: () -> Unit,
    onBack: () -> Unit,
) {
    var page by rememberSaveable { mutableStateOf(initialPage) }
    var showRemap by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    BackHandler(enabled = page != null) { page = null }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AgbaHeader(
                title = stringResource(page?.title ?: R.string.settings),
                onBack = { if (page != null) page = null else onBack() },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            when (page) {
                null -> HomePage(versionName, onOpen = { page = it }, onAbout = { showAbout = true })
                Page.APPEARANCE -> AppearancePage(settings, onLanguageChanged)
                Page.ROM_FOLDERS -> RomFoldersPage(settings, revision, onPickDirectory, onClearDirectory)
                Page.VIDEO -> VideoPage(settings)
                Page.AUDIO -> AudioPage(settings)
                Page.EMULATION -> EmulationPage(settings)
                Page.BIOS -> BiosPage(settings, revision, onPickBios, onClearBios)
                Page.TOUCH -> TouchPage(settings)
                Page.GAMEPAD -> GamepadPage(settings, onRemap = { showRemap = true })
            }
        }
    }

    if (showRemap) {
        RemapDialog(settings, revision, capturing, onCapture, onDismiss = { showRemap = false })
    }
    if (showAbout) {
        AboutDialog(versionName, onDismiss = { showAbout = false })
    }
}

@Composable
private fun HomePage(versionName: String, onOpen: (Page) -> Unit, onAbout: () -> Unit) {
    Page.entries.forEach { entry ->
        SettingsCard {
            CategoryRow(stringResource(entry.title), stringResource(entry.summary)) { onOpen(entry) }
        }
    }
    SettingsCard {
        CategoryRow(stringResource(R.string.about), stringResource(R.string.about_summary) + " · " + versionName, onAbout)
    }
}

@Composable
private fun CategoryRow(title: String, summary: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(
            painter = painterResource(R.drawable.ic_chevron),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AppearancePage(settings: Settings, onLanguageChanged: () -> Unit) {
    val context = LocalContext.current
    SettingsCard {
        ChoiceRow(
            stringResource(R.string.theme),
            ThemeMode.entries.map { stringResource(it.label) to it },
            { settings.themeMode },
            {
                settings.themeMode = it
                context.app.themeMode.value = it
            },
        )
        ChoiceRow(
            stringResource(R.string.language),
            AppLanguage.entries.map { stringResource(it.label) to it },
            { settings.language },
            {
                settings.language = it
                onLanguageChanged()
            },
        )
        ChoiceRow(
            stringResource(R.string.orientation),
            OrientationMode.entries.map { stringResource(it.label) to it },
            { settings.orientation },
            { settings.orientation = it },
        )
    }
}

@Composable
private fun RomFoldersPage(
    settings: Settings,
    revision: Int,
    onPick: (Platform) -> Unit,
    onClear: (Platform) -> Unit,
) {
    Platform.entries.forEach { platform ->
        SettingsCard {
            val directory = remember(revision) { settings.romDirectory(platform) }
            SettingRow(
                title = platform.label,
                subtitle = directory?.let { Uri.decode(Uri.parse(it).lastPathSegment.orEmpty()) } ?: stringResource(R.string.not_set),
                onClick = { onPick(platform) },
                trailing = if (directory != null) {
                    { TextButton(onClick = { onClear(platform) }) { Text(stringResource(R.string.clear)) } }
                } else {
                    null
                },
            )
        }
    }
}

@Composable
private fun VideoPage(settings: Settings) {
    val scaleOptions = ScaleMode.entries.map { stringResource(it.label) to it }
    SettingsCard {
        ChoiceRow(
            stringResource(R.string.renderer),
            Renderer.entries.map { stringResource(it.label) to it },
            { settings.renderer },
            { settings.renderer = it },
        )
    }
    NoteText(stringResource(R.string.applies_next_game))
    SettingsCard {
        ChoiceRow(stringResource(R.string.scaling_portrait), scaleOptions, { settings.scalePortrait }, { settings.scalePortrait = it })
        ChoiceRow(stringResource(R.string.scaling_landscape), scaleOptions, { settings.scaleLandscape }, { settings.scaleLandscape = it })
        ChoiceRow(
            stringResource(R.string.filter),
            VideoFilter.entries.map { stringResource(it.label) to it },
            { settings.filter },
            { settings.filter = it },
        )
    }
    NoteText(stringResource(R.string.filter_software_note))
    SettingsCard {
        ChoiceRow(stringResource(R.string.gb_palette), paletteOptions(), { settings.gbPalette }, { settings.gbPalette = it })
        ChoiceRow(stringResource(R.string.gb_model), modelOptions(), { settings.gbModel }, { settings.gbModel = it })
        SwitchRow(stringResource(R.string.sgb_borders), get = { settings.sgbBorders }, set = { settings.sgbBorders = it })
        SliderRow(stringResource(R.string.frameskip), 0..3, get = { settings.frameskip }, set = { settings.frameskip = it })
        SwitchRow(stringResource(R.string.show_fps), get = { settings.showFps }, set = { settings.showFps = it })
    }
}

@Composable
private fun AudioPage(settings: Settings) {
    SettingsCard {
        SliderRow(stringResource(R.string.volume), 0..100, step = 5, format = { "$it%" }, get = { settings.volume }, set = { settings.volume = it })
        SwitchRow(stringResource(R.string.mute), get = { settings.mute }, set = { settings.mute = it })
        ChoiceRow(
            stringResource(R.string.audio_buffer),
            listOf(
                stringResource(R.string.buffer_low) to 1024,
                stringResource(R.string.buffer_normal) to 2048,
                stringResource(R.string.buffer_high) to 4096,
            ),
            { settings.audioBuffer },
            { settings.audioBuffer = it },
        )
    }
    NoteText(stringResource(R.string.applies_next_game))
}

@Composable
private fun EmulationPage(settings: Settings) {
    val speeds = listOf(stringResource(R.string.unlimited) to 0) + listOf(2, 3, 4, 6, 8).map { "${it}x" to it }
    val rewind = listOf(stringResource(R.string.off) to 0) +
        listOf(5, 10, 30).map { stringResource(R.string.seconds_n, it) to it }
    val idle = listOf(
        stringResource(R.string.idle_remove) to "remove",
        stringResource(R.string.idle_detect) to "detect",
        stringResource(R.string.idle_ignore) to "ignore",
    )
    SettingsCard {
        ChoiceRow(stringResource(R.string.ff_speed), speeds, { settings.fastForwardSpeed }, { settings.fastForwardSpeed = it })
        ChoiceRow(stringResource(R.string.rewind_option), rewind, { settings.rewindSeconds }, { settings.rewindSeconds = it })
        ChoiceRow(stringResource(R.string.idle_opt), idle, { settings.idleOptimization }, { settings.idleOptimization = it })
        SwitchRow(stringResource(R.string.opposing), get = { settings.opposingDirections }, set = { settings.opposingDirections = it })
        SwitchRow(
            stringResource(R.string.resume_state),
            stringResource(R.string.resume_state_summary),
            get = { settings.resumeState },
            set = { settings.resumeState = it },
        )
    }
}

@Composable
private fun BiosPage(settings: Settings, revision: Int, onPick: (String) -> Unit, onClear: (String) -> Unit) {
    SettingsCard {
        SwitchRow(stringResource(R.string.use_bios), get = { settings.useBios }, set = { settings.useBios = it })
        SwitchRow(stringResource(R.string.skip_bios), get = { settings.skipBios }, set = { settings.skipBios = it })
    }
    SettingsCard {
        BIOS_FILES.forEach { (key, label) ->
            val path = remember(revision) { settings.biosPath(key) }
            SettingRow(
                title = stringResource(label),
                subtitle = stringResource(if (path != null) R.string.imported else R.string.not_set),
                onClick = { onPick(key) },
                trailing = if (path != null) {
                    { TextButton(onClick = { onClear(key) }) { Text(stringResource(R.string.remove)) } }
                } else {
                    null
                },
            )
        }
    }
}

@Composable
private fun TouchPage(settings: Settings) {
    val context = LocalContext.current
    SettingsCard {
        SwitchRow(stringResource(R.string.show_touch), get = { settings.touchControls }, set = { settings.touchControls = it })
        ChoiceRow(
            stringResource(R.string.dpad_style),
            listOf(stringResource(R.string.dpad_style_dpad) to false, stringResource(R.string.dpad_style_joystick) to true),
            { settings.joystick },
            { settings.joystick = it },
        )
        SliderRow(stringResource(R.string.opacity), 10..100, step = 5, format = { "$it%" }, get = { settings.controlsOpacity }, set = { settings.controlsOpacity = it })
        SwitchRow(stringResource(R.string.haptic), get = { settings.hapticFeedback }, set = { settings.hapticFeedback = it })
    }
    SettingsCard {
        ChoiceRow(
            stringResource(R.string.auto_hide),
            listOf(
                stringResource(R.string.off) to 0,
                stringResource(R.string.seconds_n, 10) to 10,
                stringResource(R.string.seconds_n, 30) to 30,
                stringResource(R.string.seconds_n, 45) to 45,
                stringResource(R.string.one_minute) to 60,
            ),
            { settings.autoHideSeconds },
            { settings.autoHideSeconds = it },
        )
        SwitchRow(
            stringResource(R.string.hide_on_gamepad),
            stringResource(R.string.hide_on_gamepad_summary),
            get = { settings.hideOnGamepad },
            set = { settings.hideOnGamepad = it },
        )
    }
    SettingsCard {
        SettingRow(
            stringResource(R.string.edit_layout),
            stringResource(R.string.edit_layout_summary),
            onClick = { context.startActivity(LayoutEditorActivity.intentFor(context)) },
        )
    }
}

@Composable
private fun GamepadPage(settings: Settings, onRemap: () -> Unit) {
    SettingsCard {
        SettingRow(stringResource(R.string.remap), onClick = onRemap)
        SwitchRow(stringResource(R.string.rumble), get = { settings.rumble }, set = { settings.rumble = it })
    }
    SettingsCard {
        SliderRow(stringResource(R.string.tilt), 25..300, step = 25, format = { "$it%" }, get = { settings.tiltSensitivity }, set = { settings.tiltSensitivity = it })
        SliderRow(stringResource(R.string.gyro), 25..300, step = 25, format = { "$it%" }, get = { settings.gyroSensitivity }, set = { settings.gyroSensitivity = it })
        SwitchRow(
            stringResource(R.string.light_sensor),
            stringResource(R.string.light_sensor_summary),
            get = { settings.lightSensor },
            set = { settings.lightSensor = it },
        )
        SliderRow(stringResource(R.string.solar_level), 0..10, get = { settings.solarLevel }, set = { settings.solarLevel = it })
    }
}

@Composable
private fun RemapDialog(
    settings: Settings,
    revision: Int,
    capturing: PadAction?,
    onCapture: (PadAction?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (capturing == null) onDismiss() else onCapture(null) },
        title = {
            Text(
                if (capturing == null) stringResource(R.string.remap_title)
                else stringResource(R.string.remap_press, stringResource(capturing.label)),
            )
        },
        text = {
            if (capturing == null) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    PadAction.entries.forEach { action ->
                        val code = remember(revision) { settings.padBinding(action) }
                        SettingRow(
                            title = stringResource(action.label),
                            subtitle = if (code == KeyEvent.KEYCODE_UNKNOWN) stringResource(R.string.unbound) else KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_"),
                            onClick = { onCapture(action) },
                        )
                    }
                }
            } else {
                Text(stringResource(R.string.remap_hint))
            }
        },
        confirmButton = {
            if (capturing == null) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
            } else {
                TextButton(onClick = {
                    settings.setPadBinding(capturing, KeyEvent.KEYCODE_UNKNOWN)
                    onCapture(null)
                }) { Text(stringResource(R.string.unbind)) }
            }
        },
        dismissButton = {
            if (capturing == null) {
                TextButton(onClick = {
                    settings.resetPadBindings()
                    onDismiss()
                }) { Text(stringResource(R.string.reset_all)) }
            } else {
                TextButton(onClick = { onCapture(null) }) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

@Composable
private fun AboutDialog(versionName: String, onDismiss: () -> Unit) {
    val links = LocalUriHandler.current
    var license by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = { if (license == null) onDismiss() else license = null },
        title = { Text(if (license == null) stringResource(R.string.about_title) else stringResource(R.string.licenses)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                val text = license
                if (text != null) {
                    Text(text)
                } else {
                    Image(
                        painter = painterResource(R.drawable.agba_logo),
                        contentDescription = "aGBA",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.width(150.dp).padding(bottom = 12.dp),
                    )
                    Text(stringResource(R.string.about_version, versionName), fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.about_text), modifier = Modifier.padding(top = 8.dp))
                    TextButton(onClick = { links.openUri("https://github.com/neofodere/agba") }) { Text(stringResource(R.string.agba_repo)) }
                    TextButton(onClick = { links.openUri("https://github.com/mgba-emu/mgba") }) { Text(stringResource(R.string.mgba_repo)) }
                    TextButton(onClick = { license = readLicense(context, "mgba-MPL-2.0.txt") }) { Text(stringResource(R.string.license)) }
                    TextButton(onClick = { license = readLicense(context, "inih-BSD-3-Clause.txt") }) { Text(stringResource(R.string.inih_license)) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (license == null) onDismiss() else license = null }) {
                Text(stringResource(if (license == null) R.string.close else R.string.back))
            }
        },
    )
}

private fun readLicense(context: Context, name: String): String =
    context.assets.open("licenses/$name").bufferedReader().use { it.readText() }

private fun paletteOptions(): List<Pair<String, String>> =
    NativeCore.gbPalettes().map { it to it }

@Composable
private fun modelOptions(): List<Pair<String, String>> = listOf(
    stringResource(R.string.model_auto) to "",
    "GB (DMG)" to "DMG",
    "SGB" to "SGB",
    "GBC (CGB)" to "CGB",
    "GBA (AGB)" to "AGB",
)
