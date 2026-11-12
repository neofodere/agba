package dev.neofodere.agba

import android.app.Application
import android.content.Context
import androidx.compose.runtime.mutableStateOf
import dev.neofodere.agba.data.CheatStore
import dev.neofodere.agba.data.LibraryRepository
import dev.neofodere.agba.data.Settings
import dev.neofodere.agba.data.StateStore

class AgbaApp : Application() {
    val settings by lazy { Settings(this) }
    val library by lazy { LibraryRepository(this, settings) }
    val cheats by lazy { CheatStore(this) }
    val states by lazy { StateStore(this) }
    val themeMode by lazy { mutableStateOf(settings.themeMode) }
}

val Context.app: AgbaApp get() = applicationContext as AgbaApp
