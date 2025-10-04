/*
 * Copyright (c) 2023 Auxio Project
 * DialogAlbumType.kt is part of Auxio.
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

package org.oxycblt.auxio.dialogalbums

import org.oxycblt.auxio.IntegerTable
import org.oxycblt.auxio.R

/**
 * 台词专辑类型
 */
enum class DialogAlbumType {
    DIALOG_ALBUMS;

    /**
     * The integer representation of this instance.
     */
    val intCode: Int
        get() = IntegerTable.MUSIC_MODE_DIALOG_ALBUMS

    /**
     * The string resource corresponding to this instance.
     */
    val nameRes: Int
        get() = R.string.lbl_dialog_albums

    companion object {
        /**
         * Convert a [DialogAlbumType] integer representation into an instance.
         *
         * @param intCode An integer representation of a [DialogAlbumType]
         * @return The corresponding [DialogAlbumType], or null if the [DialogAlbumType] is invalid.
         */
        fun fromIntCode(intCode: Int) =
            when (intCode) {
                IntegerTable.MUSIC_MODE_DIALOG_ALBUMS -> DIALOG_ALBUMS
                else -> null
            }
    }
}