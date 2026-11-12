package dev.neofodere.agba.data

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.StringRes
import dev.neofodere.agba.R
import dev.neofodere.agba.core.Platform

enum class ViewMode { LIST, COVERS }

enum class ThemeMode(@StringRes val label: Int) {
    AUTO(R.string.automatic),
    LIGHT(R.string.theme_light),
    DARK(R.string.theme_dark),
}

enum class OrientationMode(@StringRes val label: Int) {
    AUTO(R.string.follow_device),
    PORTRAIT(R.string.portrait),
    LANDSCAPE(R.string.landscape),
}

enum class ScaleMode(@StringRes val label: Int) {
    FIT(R.string.scale_fit),
    INTEGER(R.string.scale_integer),
    STRETCH(R.string.scale_stretch),
}

enum class Renderer(@StringRes val label: Int) {
    OPENGL(R.string.renderer_opengl),
    SOFTWARE(R.string.renderer_software),
}

enum class VideoFilter(@StringRes val label: Int, val glOnly: Boolean) {
    NEAREST(R.string.filter_nearest, false),
    LINEAR(R.string.filter_linear, false),
    SHARP(R.string.filter_sharp, true),
    FXAA(R.string.filter_fxaa, true),
}

enum class AppLanguage(@StringRes val label: Int, val tag: String?) {
    AUTO(R.string.automatic, null),
    ENGLISH(R.string.language_en, "en"),
    SPANISH(R.string.language_es, "es"),
    FRENCH(R.string.language_fr, "fr"),
}

class Settings(context: Context) {
    val prefs: SharedPreferences = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private fun bool(key: String, default: Boolean) = prefs.getBoolean(key, default)
    private fun int(key: String, default: Int) = prefs.getInt(key, default)
    private fun string(key: String): String? = prefs.getString(key, null)

    private fun putBool(key: String, value: Boolean) = prefs.edit().putBoolean(key, value).apply()
    private fun putInt(key: String, value: Int) = prefs.edit().putInt(key, value).apply()
    private fun putString(key: String, value: String?) = prefs.edit().putString(key, value).apply()

    fun romDirectory(platform: Platform): String? = string("dir_${platform.id}")
    fun setRomDirectory(platform: Platform, uri: String?) = putString("dir_${platform.id}", uri)

    var themeMode: ThemeMode
        get() = runCatching { ThemeMode.valueOf(string("theme_mode") ?: "") }.getOrDefault(ThemeMode.AUTO)
        set(value) = putString("theme_mode", value.name)

    var orientation: OrientationMode
        get() = runCatching { OrientationMode.valueOf(string("orientation") ?: "") }.getOrDefault(OrientationMode.AUTO)
        set(value) = putString("orientation", value.name)

    var viewMode: ViewMode
        get() = runCatching { ViewMode.valueOf(string("view_mode") ?: "") }.getOrDefault(ViewMode.LIST)
        set(value) = putString("view_mode", value.name)

    var useBios: Boolean
        get() = bool("use_bios", true)
        set(value) = putBool("use_bios", value)

    var skipBios: Boolean
        get() = bool("skip_bios", false)
        set(value) = putBool("skip_bios", value)

    fun biosPath(key: String): String? = string("bios_$key")
    fun setBiosPath(key: String, path: String?) = putString("bios_$key", path)

    var gbPalette: String
        get() = string("gb_palette") ?: "DMG Green"
        set(value) = putString("gb_palette", value)

    var gbModel: String
        get() = string("gb_model") ?: ""
        set(value) = putString("gb_model", value)

    var idleOptimization: String
        get() = string("idle_optimization") ?: "remove"
        set(value) = putString("idle_optimization", value)

    var frameskip: Int
        get() = int("frameskip", 0)
        set(value) = putInt("frameskip", value)

    var opposingDirections: Boolean
        get() = bool("opposing_directions", false)
        set(value) = putBool("opposing_directions", value)

    var sgbBorders: Boolean
        get() = bool("sgb_borders", false)
        set(value) = putBool("sgb_borders", value)

    var rewindSeconds: Int
        get() = int("rewind_seconds", 0)
        set(value) = putInt("rewind_seconds", value)

    var fastForwardSpeed: Int
        get() = int("fast_forward_speed", 0)
        set(value) = putInt("fast_forward_speed", value)

    var volume: Int
        get() = int("volume", 100)
        set(value) = putInt("volume", value)

    var mute: Boolean
        get() = bool("mute", false)
        set(value) = putBool("mute", value)

    var scalePortrait: ScaleMode
        get() = runCatching { ScaleMode.valueOf(string("scale_portrait") ?: "") }.getOrDefault(ScaleMode.FIT)
        set(value) = putString("scale_portrait", value.name)

    var scaleLandscape: ScaleMode
        get() = runCatching { ScaleMode.valueOf(string("scale_landscape") ?: "") }.getOrDefault(ScaleMode.FIT)
        set(value) = putString("scale_landscape", value.name)

    var renderer: Renderer
        get() = runCatching { Renderer.valueOf(string("renderer") ?: "") }.getOrDefault(Renderer.OPENGL)
        set(value) = putString("renderer", value.name)

    var filter: VideoFilter
        get() = runCatching { VideoFilter.valueOf(string("filter") ?: "") }.getOrDefault(VideoFilter.LINEAR)
        set(value) = putString("filter", value.name)

    var language: AppLanguage
        get() = runCatching { AppLanguage.valueOf(string("language") ?: "") }.getOrDefault(AppLanguage.AUTO)
        set(value) = putString("language", value.name)

    var audioBuffer: Int
        get() = int("audio_buffer", 2048)
        set(value) = putInt("audio_buffer", value)

    var showFps: Boolean
        get() = bool("show_fps", false)
        set(value) = putBool("show_fps", value)

    var touchControls: Boolean
        get() = bool("touch_controls", true)
        set(value) = putBool("touch_controls", value)

    var controlsOpacity: Int
        get() = int("controls_opacity", 60)
        set(value) = putInt("controls_opacity", value)

    var joystick: Boolean
        get() = bool("joystick", false)
        set(value) = putBool("joystick", value)

    var autoHideSeconds: Int
        get() = int("auto_hide_seconds", 0)
        set(value) = putInt("auto_hide_seconds", value)

    var hideOnGamepad: Boolean
        get() = bool("hide_on_gamepad", false)
        set(value) = putBool("hide_on_gamepad", value)

    var hapticFeedback: Boolean
        get() = bool("haptic_feedback", true)
        set(value) = putBool("haptic_feedback", value)

    var rumble: Boolean
        get() = bool("rumble", true)
        set(value) = putBool("rumble", value)

    var tiltSensitivity: Int
        get() = int("tilt_sensitivity", 100)
        set(value) = putInt("tilt_sensitivity", value)

    var gyroSensitivity: Int
        get() = int("gyro_sensitivity", 100)
        set(value) = putInt("gyro_sensitivity", value)

    var solarLevel: Int
        get() = int("solar_level", 5)
        set(value) = putInt("solar_level", value)

    var lightSensor: Boolean
        get() = bool("light_sensor", false)
        set(value) = putBool("light_sensor", value)

    var resumeState: Boolean
        get() = bool("resume_state", false)
        set(value) = putBool("resume_state", value)

    fun padBinding(action: PadAction): Int = int("pad_${action.name}", action.defaultKeyCode)
    fun setPadBinding(action: PadAction, keyCode: Int) = putInt("pad_${action.name}", keyCode)
    fun resetPadBindings() {
        val editor = prefs.edit()
        PadAction.entries.forEach { editor.remove("pad_${it.name}") }
        editor.apply()
    }

    fun layoutJson(key: String): String? = string("layout_$key")
    fun setLayoutJson(key: String, json: String?) = putString("layout_$key", json)
}
