package com.aghielaan22.label

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import java.io.File

object SearchHistoryRepository {

    private const val MAX_ITEMS = 10
    private var file: File? = null

    val recent = mutableStateListOf<String>()

    fun init(context: Context) {
        if (file != null) return
        val f = File(context.applicationContext.filesDir, "recent_searches.json")
        file = f
        if (!f.exists()) return
        try {
            val array = JSONArray(f.readText())
            for (i in 0 until array.length()) recent.add(array.getString(i))
        } catch (e: Exception) {
            // Damaged file: start empty
        }
    }

    fun add(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        recent.removeAll { it.equals(q, ignoreCase = true) }
        recent.add(0, q)
        while (recent.size > MAX_ITEMS) recent.removeAt(recent.lastIndex)
        save()
    }

    fun remove(query: String) {
        recent.remove(query)
        save()
    }

    fun clear() {
        recent.clear()
        save()
    }

    private fun save() {
        val array = JSONArray()
        recent.forEach { array.put(it) }
        file?.writeText(array.toString())
    }
}