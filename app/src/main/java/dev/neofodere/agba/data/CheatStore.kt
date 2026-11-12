package dev.neofodere.agba.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class CheatStore(context: Context) {
    private val dir = File(context.filesDir, "cheats").apply { mkdirs() }

    fun load(gameId: String): List<Cheat> {
        val file = File(dir, "$gameId.json")
        if (!file.exists()) return emptyList()
        return runCatching {
            val array = JSONArray(file.readText())
            (0 until array.length()).map {
                val item = array.getJSONObject(it)
                Cheat(item.getString("name"), item.getString("code"), item.optBoolean("enabled", true))
            }
        }.getOrDefault(emptyList())
    }

    fun save(gameId: String, cheats: List<Cheat>) {
        val array = JSONArray()
        cheats.forEach {
            array.put(JSONObject().put("name", it.name).put("code", it.code).put("enabled", it.enabled))
        }
        File(dir, "$gameId.json").writeText(array.toString())
    }
}
