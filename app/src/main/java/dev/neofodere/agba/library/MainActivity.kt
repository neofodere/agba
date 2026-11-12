package dev.neofodere.agba.library

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import dev.neofodere.agba.data.Game
import dev.neofodere.agba.emulator.EmulatorActivity
import dev.neofodere.agba.settings.CheatsActivity
import dev.neofodere.agba.settings.Page
import dev.neofodere.agba.settings.SettingsActivity
import dev.neofodere.agba.BaseActivity
import dev.neofodere.agba.R
import dev.neofodere.agba.shortcut.ShortcutHelper
import dev.neofodere.agba.ui.AgbaTheme

class MainActivity : BaseActivity() {
    private val viewModel: LibraryViewModel by viewModels()
    private var coverTarget: Game? = null

    private val settingsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.refresh()
    }

    private val coverPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val game = coverTarget
        if (uri != null && game != null) viewModel.setCover(game, uri)
        coverTarget = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) viewModel.refresh()
        setContent {
            AgbaTheme {
                LibraryScreen(
                    viewModel = viewModel,
                    onPlay = { startActivity(EmulatorActivity.intentFor(this, it.id)) },
                    onOpenSettings = { settingsLauncher.launch(SettingsActivity.intentFor(this)) },
                    onOpenRomFolders = { settingsLauncher.launch(SettingsActivity.intentFor(this, Page.ROM_FOLDERS)) },
                    onPickCover = {
                        coverTarget = it
                        coverPicker.launch(arrayOf("image/*"))
                    },
                    onShortcut = { game ->
                        val requested = ShortcutHelper.requestPin(this, game)
                        if (!requested) {
                            Toast.makeText(this, getString(R.string.shortcuts_unsupported), Toast.LENGTH_LONG).show()
                        }
                    },
                    onCheats = { startActivity(CheatsActivity.intentFor(this, it.id)) },
                )
            }
        }
    }
}
