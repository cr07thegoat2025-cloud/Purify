package com.purify.app

import android.content.Context

data class Surah(
    val number: Int,
    val arabic: String,
    val english: String,
    val meaning: String,
    val ayahs: Int,
    val meccan: Boolean,
) {
    val searchKey: String = normalize("$number $english $meaning $arabic")
    val subtitle: String get() = "${if (meccan) "Meccan" else "Medinan"} • $ayahs verses"
}

/** Lowercases and drops spaces and punctuation so "al-fatiha" and "alfatiha" match. */
fun normalize(s: String): String = s.lowercase().filter { it.isLetterOrDigit() }

object SurahRepo {
    fun load(ctx: Context): List<Surah> =
        ctx.assets.open("surahs.txt").bufferedReader().useLines { lines ->
            lines.filter { it.isNotBlank() }.map { line ->
                val p = line.split("|")
                Surah(
                    number = p[0].trim().toInt(),
                    arabic = p[1].trim(),
                    english = p[2].trim(),
                    meaning = p[3].trim(),
                    ayahs = p[4].trim().toInt(),
                    meccan = p[5].trim() == "Meccan",
                )
            }.toList()
        }
}
