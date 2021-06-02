/*
 * Copyright (c) 2018 Spotify AB
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.spotify.sdk.demo

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.annotation.ColorInt
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageButton
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.lifecycle.lifecycleScope
import com.google.gson.GsonBuilder
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.ContentApi
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.android.appremote.api.error.SpotifyDisconnectedException
import com.spotify.android.appremote.demo.R
import com.spotify.android.appremote.demo.databinding.AppRemoteLayoutBinding
import com.spotify.protocol.client.Subscription
import com.spotify.protocol.types.*
import com.spotify.sdk.demo.RemotePlayerKotActivity.AuthParams.CLIENT_ID
import com.spotify.sdk.demo.RemotePlayerKotActivity.AuthParams.REDIRECT_URI
import com.spotify.sdk.demo.RemotePlayerKotActivity.SpotifySampleContexts.ALBUM_URI
import com.spotify.sdk.demo.RemotePlayerKotActivity.SpotifySampleContexts.ARTIST_URI
import com.spotify.sdk.demo.RemotePlayerKotActivity.SpotifySampleContexts.PLAYLIST_URI
import com.spotify.sdk.demo.RemotePlayerKotActivity.SpotifySampleContexts.PODCAST_URI
import com.spotify.sdk.demo.RemotePlayerKotActivity.SpotifySampleContexts.TRACK_URI
import kotlinx.coroutines.launch
import java.util.*
import kotlin.collections.ArrayList
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

@SuppressLint("Registered")
@Suppress("UNUSED_PARAMETER")
class RemotePlayerKotActivity : AppCompatActivity() {


    object AuthParams {
        const val CLIENT_ID = "089d841ccc194c10a77afad9e1c11d54"
        const val REDIRECT_URI = "comspotifytestsdk://callback"
    }

    object SpotifySampleContexts {
        const val TRACK_URI = "spotify:track:4IWZsfEkaK49itBwCTFDXQ"
        const val ALBUM_URI = "spotify:album:4m2880jivSbbyEGAKfITCa"
        const val ARTIST_URI = "spotify:artist:3WrFJ7ztbogyGnTHbHJFl2"
        const val PLAYLIST_URI = "spotify:playlist:37i9dQZEVXbMDoHDwVN2tF"
        const val PODCAST_URI = "spotify:show:2tgPYIeGErjk6irHRhk9kj"
    }

    companion object {
        const val TAG = "App-Remote Sample"
        const val STEP_MS = 15000L
    }

    private val gson = GsonBuilder().setPrettyPrinting().create()

    private var playerStateSubscription: Subscription<PlayerState>? = null
    private var playerContextSubscription: Subscription<PlayerContext>? = null
    private var capabilitiesSubscription: Subscription<Capabilities>? = null
    private var spotifyAppRemote: SpotifyAppRemote? = null

    private lateinit var views: List<View>
    private lateinit var trackProgressBar: TrackProgressBar
    private lateinit var binding: AppRemoteLayoutBinding

    private val errorCallback = { throwable: Throwable -> logError(throwable) }

    private val playerContextEventCallback = Subscription.EventCallback<PlayerContext> { playerContext ->
        binding.currentContextLabel.apply {
            text = String.format(Locale.US, "%s\n%s", playerContext.title, playerContext.subtitle)
            tag = playerContext
        }
    }

    private val playerStateEventCallback = Subscription.EventCallback<PlayerState> { playerState ->
        Log.v(TAG, String.format("Player State: %s", gson.toJson(playerState)))

        updateShuffleButton(playerState)

        updateRepeatButton(playerState)

        updateTrackStateButton(playerState)

        updatePlayPauseButton(playerState)

        updatePlaybackSpeed(playerState)

        updateTrackCoverArt(playerState)

        updateSeekbar(playerState)
    }

    private fun updatePlayPauseButton(playerState: PlayerState) {
        // Invalidate play / pause
        if (playerState.isPaused) {
            binding.playPauseButton.setImageResource(R.drawable.btn_play)
        } else {
            binding.playPauseButton.setImageResource(R.drawable.btn_pause)
        }
    }

    private fun updateTrackStateButton(playerState: PlayerState) {
        binding.currentTrackLabel.apply {
            text = String.format(Locale.US, "%s\n%s", playerState.track.name, playerState.track.artist.name)
            tag = playerState
        }
    }

    private fun updateShuffleButton(playerState: PlayerState) {
        binding.toggleShuffleButton.apply {
            val shuffleDrawable = ResourcesCompat.getDrawable(resources, R.drawable.mediaservice_shuffle, theme)
            setImageDrawable(shuffleDrawable)
            if (!playerState.playbackOptions.isShuffling) {
                setTint(Color.WHITE)
            } else {
                setTint(resources.getColor(R.color.cat_medium_green))
            }
        }
    }

    private fun updateRepeatButton(playerState: PlayerState) {
        binding.toggleRepeatButton.apply {
            when (playerState.playbackOptions.repeatMode) {
                Repeat.ALL -> {
                    setImageResource(R.drawable.mediaservice_repeat_all)
                    setTint(resources.getColor(R.color.cat_medium_green))
                }
                Repeat.ONE -> {
                    setImageResource(R.drawable.mediaservice_repeat_one)
                    setTint(resources.getColor(R.color.cat_medium_green))
                }
                else -> {
                    setImageResource(R.drawable.mediaservice_repeat_off)
                    setTint(Color.WHITE)
                }
            }
        }
    }

    private fun AppCompatImageButton.setTint(@ColorInt tint: Int) {
        DrawableCompat.setTint(drawable, Color.WHITE)
    }

    private fun updateSeekbar(playerState: PlayerState) {
        // Update progressbar
        trackProgressBar.apply {
            if (playerState.playbackSpeed > 0) {
                unpause()
            } else {
                pause()
            }
            // Invalidate seekbar length and position
            binding.seekTo.max = playerState.track.duration.toInt()
            binding.seekTo.isEnabled = true
            setDuration(playerState.track.duration)
            update(playerState.playbackPosition)
        }
    }

    private fun updateTrackCoverArt(playerState: PlayerState) {
        // Get image from track
        assertAppRemoteConnected()
                .imagesApi
                .getImage(playerState.track.imageUri, Image.Dimension.LARGE)
                .setResultCallback { bitmap ->
                    binding.image.setImageBitmap(bitmap)
                    binding.imageLabel.text = String.format(
                            Locale.ENGLISH, "%d x %d", bitmap.width, bitmap.height)
                }
    }

    private fun updatePlaybackSpeed(playerState: PlayerState) {
        // Invalidate playback speed
        binding.playbackSpeedButton.apply {
            visibility = View.VISIBLE
            val speedIcDrawable = when (playerState.playbackSpeed) {
                0.5f -> R.drawable.ic_playback_speed_50
                0.8f -> R.drawable.ic_playback_speed_80
                1f -> R.drawable.ic_playback_speed_100
                1.2f -> R.drawable.ic_playback_speed_120
                1.5f -> R.drawable.ic_playback_speed_150
                2f -> R.drawable.ic_playback_speed_200
                3f -> R.drawable.ic_playback_speed_300
                else -> R.drawable.ic_playback_speed_100
            }
            setImageResource(speedIcDrawable)
            if (playerState.track.isPodcast && playerState.track.isEpisode) {
                isEnabled = true
                clearColorFilter()
            } else {
                isEnabled = false
                setColorFilter(Color.GRAY, PorterDuff.Mode.SRC_ATOP)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.AppTheme)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.app_remote_layout)

        binding.seekTo.apply {
            isEnabled = false
            progressDrawable.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_ATOP)
            indeterminateDrawable.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_ATOP)
        }

        trackProgressBar = TrackProgressBar(binding.seekTo) { seekToPosition: Long -> seekTo(seekToPosition) }

        views = listOf(
                binding.disconnectButton,
                binding.subscribeToPlayerContextButton,
                binding.subscribeToPlayerStateButton,
                binding.imageLabel,
                binding.imageScaleTypeLabel,
                binding.playPauseButton,
                binding.seekForwardButton,
                binding.seekBackButton,
                binding.skipPrevButton,
                binding.skipNextButton,
                binding.toggleRepeatButton,
                binding.toggleShuffleButton,
                binding.connectSwitchToLocal,
                binding.playPodcastButton,
                binding.playTrackButton,
                binding.playAlbumButton,
                binding.playArtistButton,
                binding.playPlaylistButton,
                binding.subscribeToCapabilities,
                binding.getCollectionState,
                binding.removeUri,
                binding.saveUri,
                binding.getFitnessRecommendedItemsButton,
                binding.seekTo)

        SpotifyAppRemote.setDebugMode(true)

        onDisconnected()
        onConnectAndAuthorizedClicked(binding.connectAuthorizeButton)
    }

    private fun seekTo(seekToPosition: Long) {
        assertAppRemoteConnected()
                .playerApi
                .seekTo(seekToPosition)
                .setErrorCallback(errorCallback)
    }

    override fun onStop() {
        super.onStop()
        SpotifyAppRemote.disconnect(spotifyAppRemote)
        onDisconnected()
    }

    private fun onConnected() {
        for (input in views) {
            input.isEnabled = true
        }
        binding.connectButton.apply {
            isEnabled = false
            text = getString(R.string.connected)
        }
        binding.connectAuthorizeButton.apply {
            isEnabled = false
            text = getString(R.string.connected)
        }

        onSubscribedToPlayerStateButtonClicked(binding.subscribeToPlayerStateButton)
        onSubscribedToPlayerContextButtonClicked(binding.subscribeToPlayerContextButton)
    }

    private fun onConnecting() {
        binding.connectButton.apply {
            isEnabled = false
            text = getString(R.string.connecting)
        }
        binding.connectAuthorizeButton.apply {
            isEnabled = false
            text = getString(R.string.connecting)
        }
    }

    private fun onDisconnected() {
        for (view in views) {
            view.isEnabled = false
        }
        binding.connectButton.apply {
            isEnabled = true
            text = getString(R.string.connect)
        }
        binding.connectAuthorizeButton.apply {
            isEnabled = true
            text = getString(R.string.authorize)
        }
        binding.image.setImageResource(R.drawable.widget_placeholder)
        binding.subscribeToPlayerContextButton.apply {
            visibility = View.VISIBLE
            setText(R.string.title_player_context)
        }
        binding.subscribeToPlayerStateButton.apply {
            visibility = View.VISIBLE
            setText(R.string.title_current_track)
        }
        binding.toggleRepeatButton.apply {
            clearColorFilter()
            setImageResource(R.drawable.btn_repeat)
        }
        binding.toggleShuffleButton.apply {
            clearColorFilter()
            setImageResource(R.drawable.btn_shuffle)
        }

        binding.currentContextLabel.visibility = View.INVISIBLE
        binding.currentTrackLabel.visibility = View.INVISIBLE
    }

    fun onConnectClicked(notUsed: View) {
        onConnecting()
        connect(false)
    }

    fun onConnectAndAuthorizedClicked(notUsed: View) {
        onConnecting()
        connect(true)
    }

    private fun connect(showAuthView: Boolean) {

        SpotifyAppRemote.disconnect(spotifyAppRemote)
        lifecycleScope.launch {
            try {
                spotifyAppRemote = connectToAppRemote(showAuthView)
                onConnected()
            } catch (error: Throwable) {
                onDisconnected()
                logError(error)
            }
        }
    }

    private suspend fun connectToAppRemote(showAuthView: Boolean): SpotifyAppRemote? =
            suspendCoroutine { cont: Continuation<SpotifyAppRemote> ->
                SpotifyAppRemote.connect(
                        application,
                        ConnectionParams.Builder(CLIENT_ID)
                                .setRedirectUri(REDIRECT_URI)
                                .showAuthView(showAuthView)
                                .build(),
                        object : Connector.ConnectionListener {
                            override fun onConnected(spotifyAppRemote: SpotifyAppRemote) {
                                cont.resume(spotifyAppRemote)
                            }

                            override fun onFailure(error: Throwable) {
                                cont.resumeWithException(error)
                            }
                        })
            }

    fun onDisconnectClicked(notUsed: View) {
        SpotifyAppRemote.disconnect(spotifyAppRemote)
        onDisconnected()
    }

    fun onImageClicked(view: View) {
        assertAppRemoteConnected().let {
            it.playerApi
                    .playerState
                    .setResultCallback { playerState ->
                        val popupMenu = PopupMenu(this, view)
                        popupMenu.run {
                            menu.add(720, 720, 0, "Large (720px)")
                            menu.add(480, 480, 1, "Medium (480px)")
                            menu.add(360, 360, 2, "Small (360px)")
                            menu.add(240, 240, 3, "X Small (240px)")
                            menu.add(144, 144, 4, "Thumbnail (144px)")
                            setOnMenuItemClickListener { item ->
                                it.imagesApi
                                        .getImage(
                                                playerState.track.imageUri, Image.Dimension.values()[item.order])
                                        .setResultCallback { bitmap ->
                                            binding.image.setImageBitmap(bitmap)
                                            binding.imageLabel.text = String.format(
                                                    Locale.ENGLISH,
                                                    "%d x %d",
                                                    bitmap.width,
                                                    bitmap.height)
                                        }
                                false
                            }
                            show()
                        }
                    }
                    .setErrorCallback(errorCallback)
        }
    }

    fun onImageScaleTypeClicked(view: View) {
        assertAppRemoteConnected()
                .playerApi
                .playerState
                .setResultCallback {
                    val popupMenu = PopupMenu(this, view)
                    popupMenu.run {
                        menu.add(0, ImageView.ScaleType.CENTER.ordinal, 0, "CENTER")
                        menu.add(1, ImageView.ScaleType.CENTER_CROP.ordinal, 1, "CENTER_CROP")
                        menu.add(2, ImageView.ScaleType.CENTER_INSIDE.ordinal, 2, "CENTER_INSIDE")
                        menu.add(3, ImageView.ScaleType.MATRIX.ordinal, 3, "MATRIX")
                        menu.add(4, ImageView.ScaleType.FIT_CENTER.ordinal, 4, "FIT_CENTER")
                        menu.add(4, ImageView.ScaleType.FIT_XY.ordinal, 5, "FIT_XY")
                        setOnMenuItemClickListener { item ->
                            binding.image.scaleType = ImageView.ScaleType.values()[item.itemId]
                            binding.imageLabel.text = ImageView.ScaleType.values()[item.itemId].toString()
                            false
                        }
                        show()
                    }

                }
                .setErrorCallback(errorCallback)
    }

    fun onPlayPodcastButtonClicked(notUsed: View) {
        playUri(PODCAST_URI)
    }

    fun onPlayTrackButtonClicked(notUsed: View) {
        playUri(TRACK_URI)
    }

    fun onPlayAlbumButtonClicked(notUsed: View) {
        playUri(ALBUM_URI)
    }

    fun onPlayArtistButtonClicked(notUsed: View) {
        playUri(ARTIST_URI)
    }

    fun onPlayPlaylistButtonClicked(notUsed: View) {
        playUri(PLAYLIST_URI)
    }

    private fun playUri(uri: String) {
        assertAppRemoteConnected()
                .playerApi
                .play(uri)
                .setResultCallback { logMessage(getString(R.string.command_feedback, "play")) }
                .setErrorCallback(errorCallback)
    }

    fun showCurrentPlayerContext(view: View) {
        view.tag?.let {
            showDialog("PlayerContext", gson.toJson(it))
        }
    }

    fun showCurrentPlayerState(view: View) {
        view.tag?.let {
            showDialog("PlayerState", gson.toJson(it))
        }
    }

    fun onToggleShuffleButtonClicked(notUsed: View) {
        assertAppRemoteConnected()
                .playerApi
                .toggleShuffle()
                .setResultCallback { logMessage(getString(R.string.command_feedback, "toggle shuffle")) }
                .setErrorCallback(errorCallback)
    }

    fun onToggleRepeatButtonClicked(notUsed: View) {
        assertAppRemoteConnected()
                .playerApi
                .toggleRepeat()
                .setResultCallback { logMessage(getString(R.string.command_feedback, "toggle repeat")) }
                .setErrorCallback(errorCallback)
    }

    fun onSkipPreviousButtonClicked(notUsed: View) {
        assertAppRemoteConnected()
                .playerApi
                .skipPrevious()
                .setResultCallback { logMessage(getString(R.string.command_feedback, "skip previous")) }
                .setErrorCallback(errorCallback)
    }

    fun onPlayPauseButtonClicked(notUsed: View) {
        assertAppRemoteConnected().let {
            it.playerApi
                    .playerState
                    .setResultCallback { playerState ->
                        if (playerState.isPaused) {
                            it.playerApi
                                    .resume()
                                    .setResultCallback { logMessage(getString(R.string.command_feedback, "play")) }
                                    .setErrorCallback(errorCallback)
                        } else {
                            it.playerApi
                                    .pause()
                                    .setResultCallback { logMessage(getString(R.string.command_feedback, "pause")) }
                                    .setErrorCallback(errorCallback)
                        }
                    }
        }

    }

    fun onSkipNextButtonClicked(notUsed: View) {
        assertAppRemoteConnected()
                .playerApi
                .skipNext()
                .setResultCallback { logMessage(getString(R.string.command_feedback, "skip next")) }
                .setErrorCallback(errorCallback)
    }

    fun onSeekBack(notUsed: View) {
        assertAppRemoteConnected()
                .playerApi
                .seekToRelativePosition(-STEP_MS)
                .setResultCallback { logMessage(getString(R.string.command_feedback, "seek back")) }
                .setErrorCallback(errorCallback)
    }

    fun onSeekForward(notUsed: View) {
        assertAppRemoteConnected()
                .playerApi
                .seekToRelativePosition(STEP_MS)
                .setResultCallback { logMessage(getString(R.string.command_feedback, "seek fwd")) }
                .setErrorCallback(errorCallback)
    }

    fun onSubscribeToCapabilitiesClicked(notUsed: View) {
        capabilitiesSubscription = cancelAndResetSubscription(capabilitiesSubscription)

        capabilitiesSubscription = assertAppRemoteConnected()
                .userApi
                .subscribeToCapabilities()
                .setEventCallback { capabilities ->
                    logMessage(getString(R.string.on_demand_feedback, capabilities.canPlayOnDemand))
                }
                .setErrorCallback(errorCallback) as Subscription<Capabilities>

        assertAppRemoteConnected()
                .userApi
                .capabilities
                .setResultCallback { capabilities -> logMessage(getString(R.string.on_demand_feedback, capabilities.canPlayOnDemand)) }
                .setErrorCallback(errorCallback)
    }

    fun onGetCollectionStateClicked(notUsed: View) {
        assertAppRemoteConnected()
                .userApi
                .getLibraryState(TRACK_URI)
                .setResultCallback { libraryState ->
                    showDialog(getString(R.string.command_response, getString(R.string.get_collection_state)), gson.toJson(libraryState))
                }
                .setErrorCallback { throwable -> logError(throwable) }
    }

    fun onRemoveUriClicked(notUsed: View) {
        assertAppRemoteConnected()
                .userApi
                .removeFromLibrary(TRACK_URI)
                .setResultCallback { logMessage(getString(R.string.command_feedback, getString(R.string.remove_uri))) }
                .setErrorCallback { throwable -> logError(throwable) }
    }

    fun onSaveUriClicked(notUsed: View) {
        assertAppRemoteConnected()
                .userApi
                .addToLibrary(TRACK_URI)
                .setResultCallback { logMessage(getString(R.string.command_feedback, getString(R.string.save_uri))) }
                .setErrorCallback { throwable -> logError(throwable) }
    }

    fun onGetFitnessRecommendedContentItemsClicked(notUsed: View) {
        assertAppRemoteConnected().let {
            lifecycleScope.launch {
                val combined = ArrayList<ListItem>(50)
                val listItems = loadRootRecommendations(it)
                listItems?.apply {
                    for (i in items.indices) {
                        if (items[i].playable) {
                            combined.add(items[i])
                        } else {
                            val children: ListItems? = loadChildren(it, items[i])
                            combined.addAll(convertToList(children))
                        }
                    }
                }
                showDialog(
                        getString(R.string.command_response, getString(R.string.browse_content)),
                        gson.toJson(combined))
            }
        }
    }

    private fun convertToList(inputItems: ListItems?): List<ListItem> {
        return if (inputItems?.items != null) {
            inputItems.items.toList()
        } else {
            emptyList()
        }
    }

    private suspend fun loadRootRecommendations(appRemote: SpotifyAppRemote): ListItems? =
            suspendCoroutine { cont ->
                appRemote.contentApi
                        .getRecommendedContentItems(ContentApi.ContentType.FITNESS)
                        .setResultCallback { listItems -> cont.resume(listItems) }
                        .setErrorCallback { throwable ->
                            errorCallback.invoke(throwable)
                            cont.resumeWithException(throwable)
                        }
            }

    private suspend fun loadChildren(appRemote: SpotifyAppRemote, parent: ListItem): ListItems? =
            suspendCoroutine { cont ->
                appRemote.contentApi
                        .getChildrenOfItem(parent, 6, 0)
                        .setResultCallback { listItems -> cont.resume(listItems) }
                        .setErrorCallback { throwable ->
                            errorCallback.invoke(throwable)
                            cont.resumeWithException(throwable)
                        }
            }


    fun onConnectSwitchToLocalClicked(notUsed: View) {
        assertAppRemoteConnected()
                .connectApi
                .connectSwitchToLocalDevice()
                .setResultCallback { logMessage(getString(R.string.command_feedback, getString(R.string.connect_switch_to_local))) }
                .setErrorCallback(errorCallback)
    }

    fun onSubscribedToPlayerContextButtonClicked(notUsed: View) {
        playerContextSubscription = cancelAndResetSubscription(playerContextSubscription)

        binding.currentContextLabel.visibility = View.VISIBLE
        binding.subscribeToPlayerContextButton.visibility = View.INVISIBLE
        playerContextSubscription = assertAppRemoteConnected()
                .playerApi
                .subscribeToPlayerContext()
                .setEventCallback(playerContextEventCallback)
                .setErrorCallback { throwable ->
                    binding.currentContextLabel.visibility = View.INVISIBLE
                    binding.subscribeToPlayerContextButton.visibility = View.VISIBLE
                    logError(throwable)
                } as Subscription<PlayerContext>
    }

    fun onSubscribedToPlayerStateButtonClicked(notUsed: View) {
        playerStateSubscription = cancelAndResetSubscription(playerStateSubscription)

        binding.currentTrackLabel.visibility = View.VISIBLE
        binding.subscribeToPlayerStateButton.visibility = View.INVISIBLE

        playerStateSubscription = assertAppRemoteConnected()
                .playerApi
                .subscribeToPlayerState()
                .setEventCallback(playerStateEventCallback)
                .setLifecycleCallback(
                        object : Subscription.LifecycleCallback {
                            override fun onStart() {
                                logMessage("Event: start")
                            }

                            override fun onStop() {
                                logMessage("Event: end")
                            }
                        })
                .setErrorCallback {
                    binding.currentTrackLabel.visibility = View.INVISIBLE
                    binding.subscribeToPlayerStateButton.visibility = View.VISIBLE
                } as Subscription<PlayerState>
    }

    fun onPlaybackSpeedButtonClicked(view: View) {
        val popupMenu = PopupMenu(this, view)
        popupMenu.run {
            menu.add(50, 50, 0, "0.5x")
            menu.add(80, 80, 1, "0.8x")
            menu.add(100, 100, 2, "1x")
            menu.add(120, 120, 3, "1.2x")
            menu.add(150, 150, 4, "1.5x")
            menu.add(200, 200, 5, "2x")
            menu.add(300, 300, 6, "3x")
            setOnMenuItemClickListener { item ->
                assertAppRemoteConnected()
                        .playerApi
                        .setPodcastPlaybackSpeed(PlaybackSpeed.PodcastPlaybackSpeed.values()[item.order])
                        .setResultCallback { logMessage(getString(R.string.command_feedback, getString(R.string.play_podcast_button_label))) }
                        .setErrorCallback(errorCallback)
                false
            }
            show()
        }
    }

    private fun <T : Any?> cancelAndResetSubscription(subscription: Subscription<T>?): Subscription<T>? {
        return subscription?.let {
            if (!it.isCanceled) {
                it.cancel()
            }
            null
        }
    }

    private fun assertAppRemoteConnected(): SpotifyAppRemote {
        spotifyAppRemote?.let {
            if (it.isConnected) {
                return it
            }
        }
        Log.e(TAG, getString(R.string.err_spotify_disconnected))
        throw SpotifyDisconnectedException()
    }

    private fun logError(throwable: Throwable) {
        Toast.makeText(this, R.string.err_generic_toast, Toast.LENGTH_SHORT).show()
        Log.e(TAG, "", throwable)
    }

    private fun logMessage(msg: String, duration: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(this, msg, duration).show()
        Log.d(TAG, msg)
    }

    private fun showDialog(title: String, message: String) {
        AlertDialog.Builder(this).setTitle(title).setMessage(message).create().show()
    }
}
