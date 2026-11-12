package dev.neofodere.agba.data

import android.content.Context
import java.io.File

class StateStore(context: Context) {
    private val root = File(context.getExternalFilesDir(null) ?: context.filesDir, "agba").apply { mkdirs() }
    private val savesDir = File(root, "saves").apply { mkdirs() }
    private val statesDir = File(root, "states").apply { mkdirs() }

    fun saveFile(gameId: String): File = File(savesDir, "$gameId.sav")

    private fun gameStates(gameId: String): File = File(statesDir, gameId).apply { mkdirs() }

    fun slotFile(gameId: String, slot: Int): File = File(gameStates(gameId), "slot$slot.ss")

    fun thumbnailFile(gameId: String, slot: Int): File = File(gameStates(gameId), "slot$slot.png")

    fun autoFile(gameId: String): File = File(gameStates(gameId), "auto.ss")

    companion object {
        const val SLOT_COUNT = 9
    }
}
