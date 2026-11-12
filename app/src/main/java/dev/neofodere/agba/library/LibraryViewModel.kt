package dev.neofodere.agba.library

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.neofodere.agba.AgbaApp
import dev.neofodere.agba.core.Platform
import dev.neofodere.agba.data.Game
import dev.neofodere.agba.data.ViewMode
import kotlinx.coroutines.launch

class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as AgbaApp

    var games by mutableStateOf(app.library.cachedGames())
        private set
    var scanning by mutableStateOf(false)
        private set
    var hasDirectories by mutableStateOf(app.library.hasDirectories())
        private set
    var coverRevision by mutableIntStateOf(0)
        private set
    var viewMode by mutableStateOf(app.settings.viewMode)
        private set
    var platform by mutableStateOf<Platform?>(null)
    var query by mutableStateOf("")

    fun refresh() {
        if (scanning) return
        viewModelScope.launch {
            scanning = true
            try {
                hasDirectories = app.library.hasDirectories()
                games = app.library.scan()
            } finally {
                scanning = false
            }
        }
    }

    fun toggleViewMode() {
        viewMode = if (viewMode == ViewMode.COVERS) ViewMode.LIST else ViewMode.COVERS
        app.settings.viewMode = viewMode
    }

    fun setCover(game: Game, source: Uri) {
        viewModelScope.launch {
            if (app.library.setCover(game.id, source)) coverRevision++
        }
    }

    fun removeCover(game: Game) {
        app.library.removeCover(game.id)
        coverRevision++
    }

    fun rename(game: Game, title: String) {
        app.library.setTitle(game.id, title)
        games = app.library.cachedGames()
    }

    fun hasCustomTitle(game: Game): Boolean = app.library.hasCustomTitle(game.id)

    fun hasCover(game: Game): Boolean = app.library.coverFile(game.id).exists()

    fun loadCover(game: Game, size: Int): Bitmap? = app.library.loadCover(game.id, size)
}
