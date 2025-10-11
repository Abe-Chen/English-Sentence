/*
 * Copyright (c) 2023 Auxio Project
 * SrtSubtitleParser.kt is part of Auxio.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.oxycblt.auxio.dialogimport

import java.io.InputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

private val BLOCK_SEPARATOR_REGEX = Regex("\n\s*\n")
private val TIME_REGEX = Regex("""(\d{2}):(\d{2}):(\d{2})([,.](\d{1,3}))?""")

data class SubtitleCue(
    val index: Int,
    val startMs: Long,
    val endMs: Long,
    val text: String,
)

/** Simple SRT subtitle parser that tolerates BOM markers and flexible whitespace. */
class SrtSubtitleParser {

    fun parse(input: InputStream): List<SubtitleCue> {
        val raw = decodeToString(input) ?: return emptyList()
        if (raw.isBlank()) return emptyList()

        val normalized = raw.replace("\r\n", "\n").replace('\r', '\n')
        val cleaned = normalized.removePrefix("\uFEFF").trim()
        if (cleaned.isEmpty()) return emptyList()

        val blocks = cleaned.split(BLOCK_SEPARATOR_REGEX)
        val cues = mutableListOf<SubtitleCue>()
        for (block in blocks) {
            val lines =
                block
                    .lines()
                    .map { it.trimEnd() }
                    .filter { it.isNotEmpty() }
            if (lines.size < 2) continue

            val idLine = lines[0]
            val index = idLine.toIntOrNull() ?: (cues.size + 1)

            val (startMs, endMs) = parseTiming(lines[1]) ?: continue

            val text =
                lines
                    .drop(2)
                    .joinToString("\n")
                    .trim()
            if (text.isEmpty()) continue

            cues += SubtitleCue(index, startMs, endMs, text)
        }
        return cues
    }

    private fun decodeToString(input: InputStream): String? {
        val bytes = input.readBytes()
        if (bytes.isEmpty()) return null

        val candidates =
            listOf(
                StandardCharsets.UTF_8,
                StandardCharsets.UTF_16LE,
                StandardCharsets.UTF_16BE,
                Charset.forName("windows-1252"),
            )

        candidates.forEach { charset ->
            runCatching { return String(bytes, charset) }
        }
        return String(bytes)
    }

    private fun parseTiming(line: String): Pair<Long, Long>? {
        val parts = line.split("-->")
        if (parts.size < 2) return null

        val start = parseTimestamp(parts[0]) ?: return null
        val end = parseTimestamp(parts[1]) ?: return null
        return start to end
    }

    private fun parseTimestamp(part: String): Long? {
        val match = TIME_REGEX.find(part.trim()) ?: return null
        val groups = match.groupValues
        val hours = groups[1].toLongOrNull() ?: return null
        val minutes = groups[2].toLongOrNull() ?: return null
        val seconds = groups[3].toLongOrNull() ?: return null
        val fraction = groups.getOrNull(5)?.padEnd(3, '0') ?: "000"
        val millis = fraction.toLongOrNull() ?: return null

        return ((hours * 3600) + (minutes * 60) + seconds) * 1000 + millis
    }
}
