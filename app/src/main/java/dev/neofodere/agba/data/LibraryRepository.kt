package dev.neofodere.agba.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import dev.neofodere.agba.core.Platform
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

class LibraryRepository(private val context: Context, private val settings: Settings) {
    private val libraryFile = File(context.filesDir, "library.json")
    private val namesFile = File(context.filesDir, "names.json")
    private val coversDir = File(context.filesDir, "covers").apply { mkdirs() }
    private val romCacheDir = File(context.cacheDir, "roms").apply { mkdirs() }

    private class Entry(val documentId: String, val name: String, val isDirectory: Boolean) {
        val extension: String get() = name.substringAfterLast('.', "").lowercase()
        val baseName: String get() = name.substringBeforeLast('.', name)
    }

    private fun loadNames(): Map<String, String> = runCatching {
        val json = JSONObject(namesFile.readText())
        json.keys().asSequence().associateWith { json.getString(it) }
    }.getOrDefault(emptyMap())

    private fun List<Game>.withNames(): List<Game> {
        val names = loadNames()
        if (names.isEmpty()) return this
        return map { game -> names[game.id]?.let { game.copy(title = it) } ?: game }
    }

    fun setTitle(id: String, title: String) {
        val names = loadNames().toMutableMap()
        val clean = title.trim()
        if (clean.isEmpty()) names.remove(id) else names[id] = clean
        val json = JSONObject()
        names.forEach { (key, value) -> json.put(key, value) }
        namesFile.writeText(json.toString())
    }

    fun hasCustomTitle(id: String): Boolean = loadNames().containsKey(id)

    fun cachedGames(): List<Game> =
        runCatching { Game.listFromJson(libraryFile.readText()) }.getOrDefault(emptyList()).withNames()
        .sortedBy { it.title.lowercase() }

    fun find(id: String): Game? = cachedGames().firstOrNull { it.id == id }

    fun hasDirectories(): Boolean = Platform.entries.any { settings.romDirectory(it) != null }

    suspend fun scan(): List<Game> = withContext(Dispatchers.IO) {
        val found = LinkedHashMap<String, Game>()
        for (platform in Platform.entries) {
            val tree = settings.romDirectory(platform)?.let(Uri::parse) ?: continue
            scanTree(tree, platform, found)
        }
        val games = found.values.sortedBy { it.title.lowercase() }
        libraryFile.writeText(Game.listToJson(games))
        games.withNames().sortedBy { it.title.lowercase() }
    }

    private fun scanTree(tree: Uri, platform: Platform, out: MutableMap<String, Game>) {
        val queue = ArrayDeque<Pair<String, Int>>()
        queue.addLast(DocumentsContract.getTreeDocumentId(tree) to 0)
        while (queue.isNotEmpty()) {
            val (parentId, depth) = queue.removeFirst()
            val entries = children(tree, parentId)
            val patches = entries
                .filter { !it.isDirectory && it.extension in PATCH_EXTENSIONS }
                .associateBy { it.baseName.lowercase() }
            for (entry in entries) {
                if (entry.name.startsWith(".")) continue
                if (entry.isDirectory) {
                    if (depth < MAX_DEPTH) queue.addLast(entry.documentId to depth + 1)
                    continue
                }
                if (entry.extension !in platform.extensions && entry.extension != "zip") continue
                val id = gameId(platform, entry.baseName)
                if (out.containsKey(id)) continue
                val patch = patches[entry.baseName.lowercase()]
                out[id] = Game(
                    id = id,
                    title = entry.baseName,
                    platform = platform,
                    uri = DocumentsContract.buildDocumentUriUsingTree(tree, entry.documentId).toString(),
                    fileName = entry.name,
                    patchUri = patch?.let { DocumentsContract.buildDocumentUriUsingTree(tree, it.documentId).toString() },
                )
            }
        }
    }

    private fun children(tree: Uri, parentId: String): List<Entry> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        val projection = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE)
        val entries = ArrayList<Entry>()
        try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val name = cursor.getString(1) ?: continue
                    entries += Entry(cursor.getString(0), name, cursor.getString(2) == Document.MIME_TYPE_DIR)
                }
            }
        } catch (_: Exception) {
        }
        return entries
    }

    suspend fun openRom(game: Game): Int? = withContext(Dispatchers.IO) {
        runCatching {
            if (game.isArchive) {
                val file = extractArchive(game) ?: return@runCatching null
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).detachFd()
            } else {
                context.contentResolver.openFileDescriptor(Uri.parse(game.uri), "r")?.detachFd()
            }
        }.getOrNull()
    }

    fun openPatch(game: Game): Int {
        val uri = game.patchUri ?: return -1
        return runCatching {
            context.contentResolver.openFileDescriptor(Uri.parse(uri), "r")?.detachFd()
        }.getOrNull() ?: -1
    }

    private fun extractArchive(game: Game): File? {
        val target = File(romCacheDir, game.id)
        val stream = context.contentResolver.openInputStream(Uri.parse(game.uri)) ?: return null
        ZipInputStream(stream.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val extension = entry.name.substringAfterLast('.', "").lowercase()
                if (entry.isDirectory || extension !in ROM_EXTENSIONS) continue
                FileOutputStream(target).use { zip.copyTo(it) }
                return target
            }
        }
        return null
    }

    fun coverFile(id: String): File = File(coversDir, "$id.jpg")

    fun coverVersion(id: String): Long = coverFile(id).let { if (it.exists()) it.lastModified() else 0L }

    suspend fun setCover(id: String, source: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= COVER_SIZE && bounds.outHeight / (sample * 2) >= COVER_SIZE) {
                sample *= 2
            }
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            val bitmap = context.contentResolver.openInputStream(source)?.use {
                BitmapFactory.decodeStream(it, null, options)
            } ?: return@runCatching false
            val scale = COVER_SIZE.toFloat() / maxOf(bitmap.width, bitmap.height)
            val scaled = if (scale < 1f) {
                Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
            } else {
                bitmap
            }
            FileOutputStream(coverFile(id)).use { scaled.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            true
        }.getOrDefault(false)
    }

    fun removeCover(id: String) {
        coverFile(id).delete()
    }

    fun loadCover(id: String, maxSize: Int): Bitmap? {
        val file = coverFile(id)
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxSize && bounds.outHeight / (sample * 2) >= maxSize) {
            sample *= 2
        }
        return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    companion object {
        private const val MAX_DEPTH = 4
        private const val COVER_SIZE = 512
        private val PATCH_EXTENSIONS = setOf("ips", "ups")
        private val ROM_EXTENSIONS = setOf("gb", "gbc", "gba", "agb")

        fun gameId(platform: Platform, baseName: String): String {
            val digest = MessageDigest.getInstance("SHA-1").digest("${platform.id}/$baseName".toByteArray())
            return digest.take(8).joinToString("") { "%02x".format(it) }
        }
    }
}
