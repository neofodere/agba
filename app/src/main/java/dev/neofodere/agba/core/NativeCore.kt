package dev.neofodere.agba.core

import java.nio.ByteBuffer

object NativeCore {
    init {
        System.loadLibrary("agba")
    }

    const val RESULT_OK = 0
    const val RESULT_ROM_INVALID = 1

    external fun setOption(key: String, value: String)
    external fun load(romFd: Int, patchFd: Int, savePath: String): Int
    external fun start()
    external fun shutdown()
    external fun platform(): Int
    external fun setPaused(paused: Boolean)
    external fun isPaused(): Boolean
    external fun reset()
    external fun setKeys(keys: Int)
    external fun setFastForward(enabled: Boolean)
    external fun setRewinding(enabled: Boolean)
    external fun setSensors(accelX: Float, accelY: Float, gyroZ: Float)
    external fun setLuxIndex(index: Int)
    external fun setLuxSensor(lux: Float, useSensor: Boolean)
    external fun rumbleLevel(): Float
    external fun saveState(path: String): Boolean
    external fun loadState(path: String): Boolean
    external fun setCheats(names: Array<String>, codes: Array<String>, enabled: BooleanArray)
    external fun setGbPalette(name: String)
    external fun readFrame(buffer: ByteBuffer, force: Boolean): Int
    external fun gbPalettes(): Array<String>
}
