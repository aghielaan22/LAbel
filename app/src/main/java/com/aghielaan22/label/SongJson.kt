package com.aghielaan22.label

import org.json.JSONArray
import org.json.JSONObject

fun List<Song>.toJsonArray(): JSONArray {
    val array = JSONArray()
    forEach { s ->
        array.put(
            JSONObject().apply {
                put("id", s.id)
                put("title", s.title)
                put("artist", s.artist)
                put("thumbnailUrl", s.thumbnailUrl)
            }
        )
    }
    return array
}

fun List<Song>.toJsonString(): String = toJsonArray().toString()

fun parseSongs(array: JSONArray): List<Song> =
    (0 until array.length()).map { i ->
        val o = array.getJSONObject(i)
        Song(
            id = o.getString("id"),
            title = o.getString("title"),
            artist = o.getString("artist"),
            thumbnailUrl = o.optString("thumbnailUrl").takeIf { it.isNotEmpty() }
        )
    }

fun parseSongs(json: String): List<Song> = parseSongs(JSONArray(json))