package com.aghielaan22.label

import android.icu.text.Transliterator
import android.icu.text.UnicodeSet
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal suspend fun romanizeLyricLines(lines: List<String>): Map<Int, String> {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return emptyMap()
    return withContext(Dispatchers.Default) {
        romanizeWithIcu(lines)
    }
}

@RequiresApi(Build.VERSION_CODES.Q)
private suspend fun romanizeWithIcu(lines: List<String>): Map<Int, String> {
    val eligible = UnicodeSet(
        "[[:Hangul:][:Greek:][:Cyrillic:][:scx=Hiragana:][:scx=Katakana:]]"
    ).freeze()
    // The same Han character can have different readings across languages and words.
    val han = UnicodeSet("[:Han:]").freeze()
    val context = currentCoroutineContext()
    val transliterator by lazy(LazyThreadSafetyMode.NONE) {
        try {
            Transliterator.getInstance("Any-Latin").apply { setFilter(eligible) }
        } catch (e: IllegalArgumentException) {
            Log.w("LyricRomanization", "Latin transliteration is unavailable", e)
            null
        }
    }
    val readings = mutableMapOf<Int, String>()
    for ((index, line) in lines.withIndex()) {
        context.ensureActive()
        if (!eligible.containsSome(line) || han.containsSome(line)) continue
        val reading = transliterator?.transliterate(line) ?: break
        if (reading != line && reading.isNotBlank()) readings[index] = reading
    }
    return readings
}