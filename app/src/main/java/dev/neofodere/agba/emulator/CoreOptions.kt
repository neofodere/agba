package dev.neofodere.agba.emulator

import dev.neofodere.agba.core.NativeCore
import dev.neofodere.agba.data.Settings

fun Settings.pushToCore() {
    fun flag(value: Boolean) = if (value) "1" else "0"
    NativeCore.setOption("volume", volume.toString())
    NativeCore.setOption("mute", flag(mute))
    NativeCore.setOption("fastForwardSpeed", fastForwardSpeed.toString())
    NativeCore.setOption("rewindSeconds", rewindSeconds.toString())
    NativeCore.setOption("rewindInterval", REWIND_INTERVAL.toString())
    NativeCore.setOption("useBios", flag(useBios))
    NativeCore.setOption("skipBios", flag(skipBios))
    NativeCore.setOption("biosGba", biosPath("gba").orEmpty())
    NativeCore.setOption("biosGb", biosPath("gb").orEmpty())
    NativeCore.setOption("biosGbc", biosPath("gbc").orEmpty())
    NativeCore.setOption("gbPalette", gbPalette)
    NativeCore.setOption("gbModel", gbModel)
    NativeCore.setOption("sgbBorders", flag(sgbBorders))
    NativeCore.setOption("idleOptimization", idleOptimization)
    NativeCore.setOption("tiltSensitivity", tiltSensitivity.toString())
    NativeCore.setOption("gyroSensitivity", gyroSensitivity.toString())
    NativeCore.setOption("frameskip", frameskip.toString())
    NativeCore.setOption("opposingDirections", flag(opposingDirections))
    NativeCore.setOption("audioBuffer", audioBuffer.toString())
}

private const val REWIND_INTERVAL = 4
