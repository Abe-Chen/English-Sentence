/*
 * Copyright (c) 2023 Auxio Project
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

import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import dagger.hilt.android.AndroidEntryPoint
import java.util.Formatter
import org.oxycblt.auxio.R
import org.oxycblt.auxio.databinding.FragmentDialogAlbumsBinding
import org.oxycblt.auxio.detail.DetailViewModel
import org.oxycblt.auxio.home.HomeViewModel
import org.oxycblt.auxio.list.ListFragment
import org.oxycblt.auxio.list.ListViewModel
import org.oxycblt.auxio.list.SelectableListListener
import org.oxycblt.auxio.list.adapter.SelectionIndicatorAdapter
import org.oxycblt.auxio.list.recycler.AlbumViewHolder
import org.oxycblt.auxio.list.recycler.FastScrollRecyclerView
import org.oxycblt.auxio.list.sort.Sort
import org.oxycblt.auxio.home.list.thumb
import org.oxycblt.auxio.music.MusicViewModel
import org.oxycblt.auxio.music.resolve
import org.oxycblt.auxio.playback.PlaybackViewModel
import org.oxycblt.auxio.playback.formatDurationMs
import org.oxycblt.auxio.util.collectImmediately
import org.oxycblt.musikr.Album
import org.oxycblt.musikr.Music
import org.oxycblt.musikr.MusicParent
import org.oxycblt.musikr.Song

/** Shows the list of movie dialog study albums. */
@AndroidEntryPoint
class DialogAlbumsFragment :
    ListFragment<Album, FragmentDialogAlbumsBinding>(),
    FastScrollRecyclerView.Listener,
    FastScrollRecyclerView.PopupProvider {

    private val homeModel: HomeViewModel by activityViewModels()
    private val detailModel: DetailViewModel by activityViewModels()
    override val listModel: ListViewModel by activityViewModels()
    override val musicModel: MusicViewModel by activityViewModels()
    override val playbackModel: PlaybackViewModel by activityViewModels()

    private val albumAdapter = AlbumAdapter(this)
    private val formatterSb = StringBuilder(64)
    private val formatter = Formatter(formatterSb)

    override fun onCreateBinding(inflater: LayoutInflater) =
        FragmentDialogAlbumsBinding.inflate(inflater)

    override fun onBindingCreated(
        binding: FragmentDialogAlbumsBinding,
        savedInstanceState: Bundle?
    ) {
        super.onBindingCreated(binding, savedInstanceState)

        binding.dialogAlbumsRecycler.apply {
            adapter = albumAdapter
            listener = this@DialogAlbumsFragment
            popupProvider = this@DialogAlbumsFragment
        }

        collectImmediately(homeModel.dialogAlbumList, ::updateAlbums)
        collectImmediately(listModel.selected, ::updateSelection)
        collectImmediately(
            playbackModel.song,
            playbackModel.parent,
            playbackModel.isPlaying,
            ::updatePlayback)
    }

    override fun onDestroyBinding(binding: FragmentDialogAlbumsBinding) {
        super.onDestroyBinding(binding)
        binding.dialogAlbumsRecycler.apply {
            adapter = null
            listener = null
            popupProvider = null
        }
    }

    override fun getPopup(pos: Int): String? {
        val album = homeModel.dialogAlbumList.value.getOrNull(pos) ?: return null
        return when (homeModel.albumSort.mode) {
            is Sort.Mode.ByName -> album.name.thumb()
            is Sort.Mode.ByArtist -> album.artists.firstOrNull()?.name?.thumb()
            is Sort.Mode.ByDate -> album.dates?.run { min.resolve(requireContext()) }
            is Sort.Mode.ByDuration -> album.durationMs.formatDurationMs(false)
            is Sort.Mode.ByCount -> album.songs.size.toString()
            is Sort.Mode.ByDateAdded -> {
                val dateAddedMillis = album.addedMs
                formatterSb.setLength(0)
                DateUtils.formatDateRange(
                        context,
                        formatter,
                        dateAddedMillis,
                        dateAddedMillis,
                        DateUtils.FORMAT_ABBREV_ALL)
                    .toString()
            }
            else -> null
        }
    }

    override fun onFastScrollingChanged(isFastScrolling: Boolean) {
        homeModel.setFastScrolling(isFastScrolling)
    }

    override fun onRealClick(item: Album) {
        detailModel.showAlbum(item)
    }

    override fun onOpenMenu(item: Album) {
        listModel.openMenu(R.menu.album, item)
    }

    private fun updateAlbums(albums: List<Album>) {
        val instructions = homeModel.dialogAlbumInstructions.consume()
        albumAdapter.update(albums, instructions)
        val binding = requireBinding()
        binding.dialogAlbumsEmpty.isVisible = albums.isEmpty()
        binding.dialogAlbumsRecycler.isVisible = albums.isNotEmpty()
    }

    private fun updateSelection(selection: List<Music>) {
        albumAdapter.setSelected(selection.filterIsInstanceTo(mutableSetOf()))
    }

    private fun updatePlayback(song: Song?, parent: MusicParent?, isPlaying: Boolean) {
        val album = (parent as? Album)?.takeIf { song?.album == it }
        albumAdapter.setPlaying(album, isPlaying)
    }

    private class AlbumAdapter(private val listener: SelectableListListener<Album>) :
        SelectionIndicatorAdapter<Album, AlbumViewHolder>(AlbumViewHolder.DIFF_CALLBACK) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = AlbumViewHolder.from(parent)

        override fun onBindViewHolder(holder: AlbumViewHolder, position: Int) {
            holder.bind(getItem(position), listener)
        }
    }
}