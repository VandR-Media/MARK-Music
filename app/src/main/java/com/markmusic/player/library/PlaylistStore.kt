package com.markmusic.player.library

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Persistent user playlists of MediaStore IDs. No audio bytes are copied or moved. */
object PlaylistStore {
    private const val STORAGE = "custom_playlists"
    private fun prefs(context: Context) =
        context.getSharedPreferences("mark_music", Context.MODE_PRIVATE)

    fun read(context: Context): Map<String, Set<Long>> = runCatching {
        val json = JSONObject(prefs(context).getString(STORAGE, "{}") ?: "{}")
        buildMap {
            val names = json.keys()
            while (names.hasNext()) {
                val name = names.next()
                val arr = json.optJSONArray(name) ?: JSONArray()
                put(name, (0 until arr.length()).mapNotNull { i ->
                    arr.optString(i).toLongOrNull()
                }.toSet())
            }
        }.toSortedMap(String.CASE_INSENSITIVE_ORDER)
    }.getOrDefault(emptyMap())

    private fun write(context: Context, map: Map<String, Set<Long>>) {
        val obj = JSONObject()
        map.forEach { (name, ids) ->
            obj.put(name, JSONArray(ids.map { it.toString() }))
        }
        prefs(context).edit().putString(STORAGE, obj.toString()).apply()
    }

    fun create(context: Context, title: String): Boolean {
        val name = title.trim().take(45)
        if (name.isBlank() || name.any { it == '\n' || it == '\r' }) return false
        val current = read(context)
        if (current.keys.any { it.equals(name, ignoreCase = true) }) return false
        write(context, current + (name to emptySet()))
        return true
    }

    fun add(context: Context, name: String, id: Long) {
        val current = read(context)
        val ids = current[name] ?: return
        write(context, current + (name to (ids + id)))
    }

    fun remove(context: Context, name: String, id: Long) {
        val current = read(context)
        val ids = current[name] ?: return
        write(context, current + (name to (ids - id)))
    }

    fun delete(context: Context, name: String) {
        write(context, read(context) - name)
    }
}
