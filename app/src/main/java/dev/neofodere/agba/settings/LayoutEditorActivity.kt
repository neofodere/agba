package dev.neofodere.agba.settings

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.neofodere.agba.BaseActivity
import dev.neofodere.agba.R
import dev.neofodere.agba.app
import dev.neofodere.agba.core.Platform
import dev.neofodere.agba.emulator.Control
import dev.neofodere.agba.emulator.ControlsView
import dev.neofodere.agba.emulator.EditBar
import dev.neofodere.agba.ui.AgbaTheme

class LayoutEditorActivity : BaseActivity() {
    private var platform by mutableStateOf(Platform.GBA)
    private var selected by mutableStateOf<Control?>(null)
    private var controls: ControlsView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_USER
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent { AgbaTheme { Editor() } }
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
    }

    override fun onPause() {
        controls?.persist()
        super.onPause()
    }

    private fun hideSystemBars() {
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

    @Composable
    private fun Editor() {
        val portrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            key(platform) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        ControlsView(context, platform, app.settings).apply {
                            rewindAvailable = true
                            onSelectionChanged = { this@LayoutEditorActivity.selected = it }
                            editMode = true
                            controls = this
                        }
                    },
                    onRelease = {
                        it.persist()
                        if (controls === it) controls = null
                    },
                )
            }
            Column(
                modifier = Modifier
                    .align(if (portrait) Alignment.TopCenter else Alignment.Center)
                    .statusBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Choice(stringResource(R.string.layout_gb), platform == Platform.GB) {
                        controls?.persist()
                        selected = null
                        platform = Platform.GB
                    }
                    Choice(stringResource(R.string.layout_gba), platform == Platform.GBA) {
                        controls?.persist()
                        selected = null
                        platform = Platform.GBA
                    }
                    Choice(stringResource(R.string.portrait), portrait) {
                        controls?.persist()
                        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
                    }
                    Choice(stringResource(R.string.landscape), !portrait) {
                        controls?.persist()
                        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
                    }
                }
                EditBar(
                    selected = selected,
                    onSmaller = { controls?.adjustScale(-SCALE_STEP) },
                    onLarger = { controls?.adjustScale(SCALE_STEP) },
                    onToggleVisible = { controls?.toggleSelectedVisibility() },
                    onReset = { controls?.resetLayout() },
                    onDone = ::finish,
                )
            }
        }
    }

    @Composable
    private fun Choice(label: String, active: Boolean, onClick: () -> Unit) {
        if (active) {
            Button(onClick = onClick) { Text(label) }
        } else {
            OutlinedButton(onClick = onClick) { Text(label) }
        }
    }

    companion object {
        private const val SCALE_STEP = 0.1f

        fun intentFor(context: Context): Intent = Intent(context, LayoutEditorActivity::class.java)
    }
}
