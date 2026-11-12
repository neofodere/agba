package dev.neofodere.agba.data

import android.view.KeyEvent
import androidx.annotation.StringRes
import dev.neofodere.agba.core.Keys

enum class PadAction(@StringRes val label: Int, val gameKey: Int, val defaultKeyCode: Int) {
    A(dev.neofodere.agba.R.string.control_a, Keys.A, KeyEvent.KEYCODE_BUTTON_B),
    B(dev.neofodere.agba.R.string.control_b, Keys.B, KeyEvent.KEYCODE_BUTTON_A),
    SELECT(dev.neofodere.agba.R.string.control_select, Keys.SELECT, KeyEvent.KEYCODE_BUTTON_SELECT),
    START(dev.neofodere.agba.R.string.control_start, Keys.START, KeyEvent.KEYCODE_BUTTON_START),
    UP(dev.neofodere.agba.R.string.pad_up, Keys.UP, KeyEvent.KEYCODE_DPAD_UP),
    DOWN(dev.neofodere.agba.R.string.pad_down, Keys.DOWN, KeyEvent.KEYCODE_DPAD_DOWN),
    LEFT(dev.neofodere.agba.R.string.pad_left, Keys.LEFT, KeyEvent.KEYCODE_DPAD_LEFT),
    RIGHT(dev.neofodere.agba.R.string.pad_right, Keys.RIGHT, KeyEvent.KEYCODE_DPAD_RIGHT),
    L(dev.neofodere.agba.R.string.control_l, Keys.L, KeyEvent.KEYCODE_BUTTON_L1),
    R(dev.neofodere.agba.R.string.control_r, Keys.R, KeyEvent.KEYCODE_BUTTON_R1),
    FAST_FORWARD(dev.neofodere.agba.R.string.fast_forward, 0, KeyEvent.KEYCODE_BUTTON_R2),
    REWIND(dev.neofodere.agba.R.string.control_rewind, 0, KeyEvent.KEYCODE_BUTTON_L2),
    MENU(dev.neofodere.agba.R.string.control_menu, 0, KeyEvent.KEYCODE_BUTTON_MODE),
    QUICK_SAVE(dev.neofodere.agba.R.string.quick_save, 0, KeyEvent.KEYCODE_UNKNOWN),
    QUICK_LOAD(dev.neofodere.agba.R.string.quick_load, 0, KeyEvent.KEYCODE_UNKNOWN),
}
