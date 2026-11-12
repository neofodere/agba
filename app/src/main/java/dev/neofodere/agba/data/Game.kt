package dev.neofodere.agba.data

import dev.neofodere.agba.core.Platform
import org.json.JSONArray
import org.json.JSONObject

data class Game(
    val id: String,
    val title: String,
    val platform: Platform,
    val uri: String,
    val fileName: String,
    val patchUri: String?,
) {
    val isArchive: Boolean get() = fileName.endsWith(".zip", ignoreCase = true)

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("platform", platform.id)
        .put("uri", uri)
        .put("file", fileName)
        .put("patch", patchUri ?: JSONObject.NULL)

    companion object {
        fun fromJson(json: JSONObject): Game? {
            val platform = Platform.fromId(json.optString("platform")) ?: return null
            return Game(
                id = json.getString("id"),
                title = json.getString("title"),
                platform = platform,
                uri = json.getString("uri"),
                fileName = json.optString("file", json.getString("title")),
                patchUri = if (json.isNull("patch")) null else json.optString("patch"),
            )
        }

        fun listToJson(games: List<Game>): String {
            val array = JSONArray()
            games.forEach { array.put(it.toJson()) }
            return array.toString()
        }

        fun listFromJson(text: String): List<Game> {
            val array = JSONArray(text)
            return (0 until array.length()).mapNotNull { fromJson(array.getJSONObject(it)) }
        }
    }
}

data class Cheat(
    val name: String,
    val code: String,
    val enabled: Boolean,
)
