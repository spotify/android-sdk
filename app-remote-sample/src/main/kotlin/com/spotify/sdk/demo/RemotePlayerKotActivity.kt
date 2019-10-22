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

package com.spotify.sdk.demo.kotlin

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Bundle
import android.support.annotation.ColorInt
import android.support.v4.app.FragmentActivity
import android.support.v4.content.res.ResourcesCompat
import android.support.v4.graphics.drawable.DrawableCompat
import android.support.v7.app.AlertDialog
import android.support.v7.widget.AppCompatImageButton
import android.support.v7.widget.PopupMenu
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import com.google.gson.GsonBuilder
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.ContentApi
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.android.appremote.api.error.SpotifyDisconnectedException
import com.spotify.android.appremote.demo.R
import com.spotify.protocol.client.Subscription
import com.spotify.protocol.types.*
import com.spotify.sdk.demo.kotlin.RemotePlayerKotActivity.AuthParams.CLIENT_ID
import com.spotify.sdk.demo.kotlin.RemotePlayerKotActivity.AuthParams.REDIRECT_URI
import com.spotify.sdk.demo.kotlin.RemotePlayerKotActivity.SpotifySampleContexts.ALBUM_URI
import com.spotify.sdk.demo.kotlin.RemotePlayerKotActivity.SpotifySampleContexts.ARTIST_URI
import com.spotify.sdk.demo.kotlin.RemotePlayerKotActivity.SpotifySampleContexts.PLAYLIST_URI
import com.spotify.sdk.demo.kotlin.RemotePlayerKotActivity.SpotifySampleContexts.PODCAST_URI
import com.spotify.sdk.demo.kotlin.RemotePlayerKotActivity.SpotifySampleContexts.TRACK_URI
import kotlinx.android.synthetic.main.app_remote_layout.*
import java.util.*
import java.util.concurrent.CountDownLatch
import kotlin.collections.ArrayList

@SuppressLint("Registered")
@Suppress("UNUSED_PARAMETER")
class RemotePlayerKotActivity : FragmentActivity() {


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
        const val STEP_MS = 15000L;
    }

    private val gson = GsonBuilder().setPrettyPrinting().create()

    private var playerStateSubscription: Subscription<PlayerState>? = null
    private var playerContextSubscription: Subscription<PlayerContext>? = null
    private var capabilitiesSubscription: Subscription<Capabilities>? = null
    private var spotifyAppRemote: SpotifyAppRemote? = null

    private lateinit var views: List<View>
    private lateinit var trackProgressBar: TrackProgressBar

    private val errorCallback = { throwable: Throwable -> logError(throwable, getString(R.string.err_generic_toast)) }

    private val playerContextEventCallback = Subscription.EventCallback<PlayerContext> { playerContext ->
        current_context_label.apply {
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
            play_pause_button.setImageResource(R.drawable.btn_play)
        } else {
            play_pause_button.setImageResource(R.drawable.btn_pause)
        }
    }

    private fun updateTrackStateButton(playerState: PlayerState) {
        current_track_label.apply {
            text = String.format(Locale.US, "%s\n%s", playerState.track.name, playerState.track.artist.name)
            tag = playerState
        }
    }

    private fun updateShuffleButton(playerState: PlayerState) {
        toggle_shuffle_button.apply {
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
        toggle_repeat_button.apply {
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

    private fun AppCompatImageButton.setTint(@ColorInt tint: Int): Unit {
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
            seek_to.max = playerState.track.duration.toInt()
            seek_to.isEnabled = true
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
                    image.setImageBitmap(bitmap)
                    image_label.text = String.format(
                            Locale.ENGLISH, "%d x %d", bitmap.width, bitmap.height)
                }
    }

    private fun updatePlaybackSpeed(playerState: PlayerState) {
        // Invalidate playback speed
        playback_speed_button.apply {
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

        seek_to.apply {
            isEnabled = false
            progressDrawable.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_ATOP)
            indeterminateDrawable.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_ATOP)
        }

        trackProgressBar = TrackProgressBar(seek_to) { seekToPosition: Long -> seekTo(seekToPosition) }

        views = listOf(
                disconnect_button,
                subscribe_to_player_context_button,
                subscribe_to_player_state_button,
                image_label,
                image_scale_type_label,
                play_pause_button,
                seek_forward_button,
                seek_back_button,
                skip_prev_button,
                skip_next_button,
                toggle_repeat_button,
                toggle_shuffle_button,
                connect_switch_to_local,
                play_podcast_button,
                play_track_button,
                play_album_button,
                play_artist_button,
                play_playlist_button,
                subscribe_to_capabilities,
                get_collection_state,
                remove_uri,
                save_uri,
                get_fitness_recommended_items_button,
                seek_to)

        SpotifyAppRemote.setDebugMode(true)

        onDisconnected()
        onConnectAndAuthorizedClicked(connect_authorize_button)
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
        connect_button.apply {
            isEnabled = false
            text = getString(R.string.connected)
        }
        connect_authorize_button.apply {
            isEnabled = false
            text = getString(R.string.connected)
        }

        onSubscribedToPlayerStateButtonClicked(subscribe_to_player_state_button)
        onSubscribedToPlayerContextButtonClicked(subscribe_to_player_context_button)
    }

    private fun onConnecting() {
        connect_button.apply {
            isEnabled = false
            text = getString(R.string.connecting)
        }
        connect_authorize_button.apply {
            isEnabled = false
            text = getString(R.string.connecting)
        }
    }

    private fun onDisconnected() {
        for (view in views) {
            view.isEnabled = false
        }
        connect_button.apply {
            isEnabled = true
            text = getString(R.string.connect)
        }
        connect_authorize_button.apply {
            isEnabled = true
            text = getString(R.string.authorize)
        }
        image.setImageResource(R.drawable.widget_placeholder)
        subscribe_to_player_context_button.apply {
            visibility = View.VISIBLE
            setText(R.string.title_player_context)
        }
        subscribe_to_player_state_button.apply {
            visibility = View.VISIBLE
            setText(R.string.title_current_track)
        }
        toggle_repeat_button.apply {
            clearColorFilter()
            setImageResource(R.drawable.btn_repeat)
        }
        toggle_shuffle_button.apply {
            clearColorFilter()
            setImageResource(R.drawable.btn_shuffle)
        }

        current_context_label.visibility = View.INVISIBLE
        current_track_label.visibility = View.INVISIBLE
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

        SpotifyAppRemote.connect(
                application,
                ConnectionParams.Builder(CLIENT_ID)
                        .setRedirectUri(REDIRECT_URI)
                        .showAuthView(showAuthView)
                        .build(),
                object : Connector.ConnectionListener {
                    override fun onConnected(spotifyAppRemote: SpotifyAppRemote) {
                        this@RemotePlayerKotActivity.spotifyAppRemote = spotifyAppRemote
                        this@RemotePlayerKotActivity.onConnected()
                    }

                    override fun onFailure(error: Throwable) {
                        logError(error, String.format("Connection failed: %s", error))
                        this@RemotePlayerKotActivity.onDisconnected()
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
                                            image.setImageBitmap(bitmap)
                                            image_label.text = String.format(
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
                            image.scaleType = ImageView.ScaleType.values()[item.itemId]
                            image_label.text = ImageView.ScaleType.values()[item.itemId].toString()
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
                .setResultCallback { logMessage("Play successful") }
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
                .setResultCallback { logMessage("Toggle shuffle successful") }
                .setErrorCallback(errorCallback)
    }

    fun onToggleRepeatButtonClicked(notUsed: View) {
        assertAppRemoteConnected()
                .playerApi
                .toggleRepeat()
                .setResultCallback { logMessage("Toggle repeat successful") }
                .setErrorCallback(errorCallback)
    }

    fun onSetShuffleTrueButtonClicked(notUsed: View) {
        assertAppRemoteConnected()
                .playerApi
                .setShuffle(true)
                .setResultCallback { logMessage("Set shuffle true successful") }
                .setErrorCallback(errorCallback)
    }

    fun onSetRepeatAllButtonClicked(notUsed: View) {
        assertAppRemoteConnected()
                .playerApi
                .setRepeat(Repeat.ALL)
                .setResultCallback { logMessage("Set repeat ALL successful") }
                .setErrorCallback(errorCallback)
    }

    fun onSkipPreviousButtonClicked(notUsed: View) {
        assertAppRemoteConnected()
                .playerApi
                .skipPrevious()
                .setResultCallback { logMessage("Skip previous successful") }
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
                                    .setResultCallback { logMessage("Play current track successful") }
                                    .setErrorCallback(errorCallback)
                        } else {
                            it.playerApi
                                    .pause()
                                    .setResultCallback { logMessage("Pause successful") }
                                    .setErrorCallback(errorCallback)
                        }
                    }
        }

    }

    fun onSkipNextButtonClicked(notUsed: View) {
        assertAppRemoteConnected()
                .playerApi
                .skipNext()
                .setResultCallback { logMessage("Skip next successful") }
                .setErrorCallback(errorCallback)
    }

    fun onSeekBack(notUsed: View) {
        assertAppRemoteConnected()
                .playerApi
                .seekToRelativePosition(-STEP_MS)
                .setResultCallback { logMessage("Seek back 15 sec successful") }
                .setErrorCallback(errorCallback)
    }

    fun onSeekForward(notUsed: View) {
        assertAppRemoteConnected()
                .playerApi
                .seekToRelativePosition(STEP_MS)
                .setResultCallback { logMessage("Seek forward 15 sec successful") }
                .setErrorCallback(errorCallback)
    }

    fun onSubscribeToCapabilitiesClicked(notUsed: View) {
        capabilitiesSubscription = cancelAndResetSubscription(capabilitiesSubscription)

        capabilitiesSubscription = assertAppRemoteConnected()
                .userApi
                .subscribeToCapabilities()
                .setEventCallback { capabilities ->
                    logMessage(
                            String.format("Can play on demand: %s", capabilities.canPlayOnDemand))
                }
                .setErrorCallback(errorCallback) as Subscription<Capabilities>

        assertAppRemoteConnected()
                .userApi
                .capabilities
                .setResultCallback { capabilities -> logMessage(String.format("Can play on demand: %s", capabilities.canPlayOnDemand)) }
                .setErrorCallback(errorCallback)
    }

    fun onGetCollectionStateClicked(notUsed: View) {
        assertAppRemoteConnected()
                .userApi
                .getLibraryState(TRACK_URI)
                .setResultCallback { libraryState ->
                    logMessage(
                            String.format(
                                    "Item is in collection: %s\nCan be added to collection: %s",
                                    libraryState.isAdded, libraryState.canAdd))
                }
                .setErrorCallback { t -> logError(t, "Error:" + t.message) }
    }

    fun onRemoveUriClicked(notUsed: View) {
        assertAppRemoteConnected()
                .userApi
                .removeFromLibrary(TRACK_URI)
                .setResultCallback { logMessage("Remove from collection successful") }
                .setErrorCallback { throwable -> logError(throwable, "Error:" + throwable.message) }
    }

    fun onSaveUriClicked(notUsed: View) {
        assertAppRemoteConnected()
                .userApi
                .addToLibrary(TRACK_URI)
                .setResultCallback { logMessage("Add to collection successful") }
                .setErrorCallback { throwable -> logError(throwable, "Error:" + throwable.message) }
    }

    fun onGetFitnessRecommendedContentItemsClicked(notUsed: View) {
        assertAppRemoteConnected().let {
            it.contentApi
                    .getRecommendedContentItems(ContentApi.ContentType.FITNESS)
                    .setResultCallback { listItems ->
                        val latch = CountDownLatch(listItems.items.size)
                        val combined = ArrayList<ListItem>(50)
                        for (j in listItems.items.indices) {
                            if (listItems.items[j].playable) {
                                combined.add(listItems.items[j])
                                handleLatch(latch, combined)
                            } else {
                                it.contentApi
                                        .getChildrenOfItem(listItems.items[j], 3, 0)
                                        .setResultCallback { childListItems ->
                                            combined.addAll(listOf(*childListItems.items))
                                            handleLatch(latch, combined)
                                        }
                                        .setErrorCallback(errorCallback)
                            }
                        }
                    }
                    .setErrorCallback(errorCallback)
        }

    }

    private fun handleLatch(latch: CountDownLatch, combined: List<ListItem>) {
        latch.countDown()
        if (latch.count == 0L) {
            showDialog("RecommendedContentItems", gson.toJson(combined))
        }
    }

    fun onConnectSwitchToLocalClicked(notUsed: View) {
        assertAppRemoteConnected()
                .connectApi
                .connectSwitchToLocalDevice()
                .setResultCallback { logMessage("Success!") }
                .setErrorCallback(errorCallback)
    }

    fun onSubscribedToPlayerContextButtonClicked(notUsed: View) {
        playerContextSubscription = cancelAndResetSubscription(playerContextSubscription)

        current_context_label.visibility = View.VISIBLE
        subscribe_to_player_context_button.visibility = View.INVISIBLE
        playerContextSubscription = assertAppRemoteConnected()
                .playerApi
                .subscribeToPlayerContext()
                .setEventCallback(playerContextEventCallback)
                .setErrorCallback { throwable ->
                    current_context_label.visibility = View.INVISIBLE
                    subscribe_to_player_context_button.visibility = View.VISIBLE
                    logError(throwable, "Subscribed to PlayerContext failed!")
                } as Subscription<PlayerContext>
    }

    fun onSubscribedToPlayerStateButtonClicked(notUsed: View) {
        playerStateSubscription = cancelAndResetSubscription(playerStateSubscription)

        current_track_label.visibility = View.VISIBLE
        subscribe_to_player_state_button.visibility = View.INVISIBLE

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
                    current_track_label.visibility = View.INVISIBLE
                    subscribe_to_player_state_button.visibility = View.VISIBLE
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
                        .setResultCallback { logMessage("Play podcast successful") }
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
                return it;
            }
        }
        Log.e(TAG, getString(R.string.err_spotify_disconnected))
        throw SpotifyDisconnectedException()
    }

    private fun logError(throwable: Throwable, msg: String) {
        Toast.makeText(this, "Error: $msg", Toast.LENGTH_SHORT).show()
        Log.e(TAG, msg, throwable)
    }

    private fun logMessage(msg: String, duration: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(this, msg, duration).show()
        Log.d(TAG, msg)
    }

    private fun showDialog(title: String, message: String) {
        AlertDialog.Builder(this).setTitle(title).setMessage(message).create().show()
    }
}
