package dev.neofodere.agba.emulator

import androidx.annotation.StringRes
import dev.neofodere.agba.core.Platform
import org.json.JSONObject

enum class Control(val label: String, @StringRes val title: Int) {
    DPAD("", dev.neofodere.agba.R.string.control_dpad),
    A("A", dev.neofodere.agba.R.string.control_a),
    B("B", dev.neofodere.agba.R.string.control_b),
    START("Start", dev.neofodere.agba.R.string.control_start),
    SELECT("Select", dev.neofodere.agba.R.string.control_select),
    L("L", dev.neofodere.agba.R.string.control_l),
    R("R", dev.neofodere.agba.R.string.control_r),
    MENU("", dev.neofodere.agba.R.string.control_menu),
    REWIND("", dev.neofodere.agba.R.string.control_rewind),
    FAST_FORWARD("", dev.neofodere.agba.R.string.control_fast_forward),
}

class Placement(var x: Float, var y: Float, var scale: Float = 1f, var visible: Boolean = true)

class ControlLayout(val placements: MutableMap<Control, Placement>) {
    fun toJson(): String {
        val root = JSONObject()
        placements.forEach { (control, p) ->
            root.put(
                control.name,
                JSONObject().put("x", p.x.toDouble()).put("y", p.y.toDouble())
                    .put("scale", p.scale.toDouble()).put("visible", p.visible),
            )
        }
        return root.toString()
    }

    companion object {
        fun key(platform: Platform, landscape: Boolean): String =
            "${if (platform.hasShoulders) "gba" else "gb"}_${if (landscape) "land" else "port"}"

        fun fromJson(text: String?, platform: Platform, landscape: Boolean): ControlLayout {
            val layout = defaults(platform, landscape)
            if (text == null) return layout
            runCatching {
                val root = JSONObject(text)
                Control.entries.forEach { control ->
                    val item = root.optJSONObject(control.name) ?: return@forEach
                    layout.placements[control] = Placement(
                        x = item.optDouble("x", 0.5).toFloat().coerceIn(0f, 1f),
                        y = item.optDouble("y", 0.5).toFloat().coerceIn(0f, 1f),
                        scale = item.optDouble("scale", 1.0).toFloat().coerceIn(0.5f, 2.5f),
                        visible = item.optBoolean("visible", true),
                    )
                }
            }
            return layout
        }

        fun defaults(platform: Platform, landscape: Boolean): ControlLayout {
            val shoulders = platform.hasShoulders
            val map = if (landscape) {
                mutableMapOf(
                    Control.DPAD to Placement(0.14f, 0.62f),
                    Control.A to Placement(0.92f, 0.54f),
                    Control.B to Placement(0.82f, 0.70f),
                    Control.SELECT to Placement(0.42f, 0.92f),
                    Control.START to Placement(0.58f, 0.92f),
                    Control.L to Placement(0.09f, 0.14f, visible = shoulders),
                    Control.R to Placement(0.91f, 0.14f, visible = shoulders),
                    Control.MENU to Placement(0.50f, 0.07f),
                    Control.REWIND to Placement(0.40f, 0.07f),
                    Control.FAST_FORWARD to Placement(0.60f, 0.07f),
                )
            } else {
                mutableMapOf(
                    Control.DPAD to Placement(0.24f, 0.78f),
                    Control.A to Placement(0.87f, 0.73f),
                    Control.B to Placement(0.69f, 0.82f),
                    Control.SELECT to Placement(0.36f, 0.95f),
                    Control.START to Placement(0.64f, 0.95f),
                    Control.L to Placement(0.14f, 0.58f, visible = shoulders),
                    Control.R to Placement(0.86f, 0.58f, visible = shoulders),
                    Control.MENU to Placement(0.50f, 0.60f),
                    Control.REWIND to Placement(0.38f, 0.60f),
                    Control.FAST_FORWARD to Placement(0.62f, 0.60f),
                )
            }
            return ControlLayout(map)
        }
    }
}
