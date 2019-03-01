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

package com.spotify.sdk.demo;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.AppCompatImageButton;
import android.support.v7.widget.AppCompatSeekBar;
import android.support.v7.widget.AppCompatTextView;
import android.support.v7.widget.PopupMenu;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.spotify.android.appremote.api.ConnectionParams;
import com.spotify.android.appremote.api.Connector;
import com.spotify.android.appremote.api.ContentApi;
import com.spotify.android.appremote.api.SpotifyAppRemote;
import com.spotify.android.appremote.api.error.AuthenticationFailedException;
import com.spotify.android.appremote.api.error.CouldNotFindSpotifyApp;
import com.spotify.android.appremote.api.error.LoggedOutException;
import com.spotify.android.appremote.api.error.NotLoggedInException;
import com.spotify.android.appremote.api.error.OfflineModeException;
import com.spotify.android.appremote.api.error.SpotifyConnectionTerminatedException;
import com.spotify.android.appremote.api.error.SpotifyDisconnectedException;
import com.spotify.android.appremote.api.error.SpotifyRemoteServiceException;
import com.spotify.android.appremote.api.error.UnsupportedFeatureVersionException;
import com.spotify.android.appremote.api.error.UserNotAuthorizedException;
import com.spotify.android.appremote.demo.R;
import com.spotify.protocol.client.ErrorCallback;
import com.spotify.protocol.client.Subscription;
import com.spotify.protocol.types.Capabilities;
import com.spotify.protocol.types.Image;
import com.spotify.protocol.types.ListItem;
import com.spotify.protocol.types.PlaybackSpeed;
import com.spotify.protocol.types.PlayerContext;
import com.spotify.protocol.types.PlayerState;
import com.spotify.protocol.types.Repeat;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class RemotePlayerActivity extends FragmentActivity {

    private static final String TAG = RemotePlayerActivity.class.getSimpleName();

    private static final String CLIENT_ID = "089d841ccc194c10a77afad9e1c11d54";
    private static final String REDIRECT_URI = "comspotifytestsdk://callback";

    private static final String TRACK_URI = "spotify:track:4IWZsfEkaK49itBwCTFDXQ";
    private static final String ALBUM_URI = "spotify:album:4nZ5wPL5XxSY2OuDgbnYdc";
    private static final String ARTIST_URI = "spotify:artist:3WrFJ7ztbogyGnTHbHJFl2";
    private static final String PLAYLIST_URI = "spotify:playlist:37i9dQZEVXbMDoHDwVN2tF";
    private static final String PODCAST_URI = "spotify:show:2tgPYIeGErjk6irHRhk9kj";

    private static SpotifyAppRemote mSpotifyAppRemote;

    Gson gson = new GsonBuilder().setPrettyPrinting().create();

    Button mConnectButton, mConnectAuthorizeButton;
    Button mSubscribeToPlayerContextButton;
    Button mPlayerContextButton;
    Button mSubscribeToPlayerStateButton;
    Button mPlayerStateButton;
    ImageView mCoverArtImageView;
    AppCompatTextView mImageLabel;
    AppCompatTextView mImageScaleTypeLabel;
    AppCompatImageButton mToggleShuffleButton;
    AppCompatImageButton mPlayPauseButton;
    AppCompatImageButton mToggleRepeatButton;
    AppCompatSeekBar mSeekBar;
    AppCompatImageButton mPlaybackSpeedButton;

    List<View> mViews;
    TrackProgressBar mTrackProgressBar;

    Subscription<PlayerState> mPlayerStateSubscription;
    Subscription<PlayerContext> mPlayerContextSubscription;
    Subscription<Capabilities> mCapabilitiesSubscription;

    private final ErrorCallback mErrorCallback = throwable -> logError(throwable, "Boom!");

    @SuppressLint("SetTextI18n")
    private final Subscription.EventCallback<PlayerContext> mPlayerContextEventCallback = new Subscription.EventCallback<PlayerContext>() {
        @Override
        public void onEvent(PlayerContext playerContext) {
            mPlayerContextButton.setText(String.format(Locale.US, "%s\n%s", playerContext.title, playerContext.subtitle));
            mPlayerContextButton.setTag(playerContext);
        }
    };

    @SuppressLint("SetTextI18n")
    private final Subscription.EventCallback<PlayerState> mPlayerStateEventCallback = new Subscription.EventCallback<PlayerState>() {
        @Override
        public void onEvent(PlayerState playerState) {

            Drawable drawable = ResourcesCompat.getDrawable(getResources(), R.drawable.mediaservice_shuffle, getTheme());
            if (!playerState.playbackOptions.isShuffling) {
                mToggleShuffleButton.setImageDrawable(drawable);
                DrawableCompat.setTint(mToggleShuffleButton.getDrawable(), Color.WHITE);
            } else {
                mToggleShuffleButton.setImageDrawable(drawable);
                DrawableCompat.setTint(mToggleShuffleButton.getDrawable(), getResources().getColor(R.color.cat_medium_green));
            }

            if (playerState.playbackOptions.repeatMode == Repeat.ALL) {
                mToggleRepeatButton.setImageResource(R.drawable.mediaservice_repeat_all);
                DrawableCompat.setTint(mToggleRepeatButton.getDrawable(), getResources().getColor(R.color.cat_medium_green));
            } else if (playerState.playbackOptions.repeatMode == Repeat.ONE) {
                mToggleRepeatButton.setImageResource(R.drawable.mediaservice_repeat_one);
                DrawableCompat.setTint(mToggleRepeatButton.getDrawable(), getResources().getColor(R.color.cat_medium_green));
            } else {
                mToggleRepeatButton.setImageResource(R.drawable.mediaservice_repeat_off);
                DrawableCompat.setTint(mToggleRepeatButton.getDrawable(), Color.WHITE);
            }

            mPlayerStateButton.setText(String.format(Locale.US, "%s\n%s", playerState.track.name, playerState.track.artist.name));
            mPlayerStateButton.setTag(playerState);

            // Update progressbar
            if (playerState.playbackSpeed > 0) {
                mTrackProgressBar.unpause();
            } else {
                mTrackProgressBar.pause();
            }

            // Invalidate play / pause
            if (playerState.isPaused) {
                mPlayPauseButton.setImageResource(R.drawable.btn_play);
            } else {
                mPlayPauseButton.setImageResource(R.drawable.btn_pause);
            }

            // Invalidate playback speed
            mPlaybackSpeedButton.setVisibility(View.VISIBLE);
            if (playerState.playbackSpeed == 0.5f) {
                mPlaybackSpeedButton.setImageResource(R.drawable.ic_playback_speed_50);
            } else if (playerState.playbackSpeed == 0.8f) {
                mPlaybackSpeedButton.setImageResource(R.drawable.ic_playback_speed_80);
            } else if (playerState.playbackSpeed == 1f) {
                mPlaybackSpeedButton.setImageResource(R.drawable.ic_playback_speed_100);
            } else if (playerState.playbackSpeed == 1.2f) {
                mPlaybackSpeedButton.setImageResource(R.drawable.ic_playback_speed_120);
            } else if (playerState.playbackSpeed == 1.5f) {
                mPlaybackSpeedButton.setImageResource(R.drawable.ic_playback_speed_150);
            } else if (playerState.playbackSpeed == 2f) {
                mPlaybackSpeedButton.setImageResource(R.drawable.ic_playback_speed_200);
            } else if (playerState.playbackSpeed == 3f) {
                mPlaybackSpeedButton.setImageResource(R.drawable.ic_playback_speed_300);
            }
            if (playerState.track.isPodcast && playerState.track.isEpisode) {
                mPlaybackSpeedButton.setEnabled(true);
                mPlaybackSpeedButton.clearColorFilter();
            } else {
                mPlaybackSpeedButton.setEnabled(false);
                mPlaybackSpeedButton.setColorFilter(Color.GRAY, PorterDuff.Mode.SRC_ATOP);
            }

            // Get image from track
            mSpotifyAppRemote.getImagesApi()
                    .getImage(playerState.track.imageUri, Image.Dimension.LARGE)
                    .setResultCallback(bitmap -> {
                        mCoverArtImageView.setImageBitmap(bitmap);
                        mImageLabel.setText(String.format(Locale.ENGLISH, "%d x %d", bitmap.getWidth(), bitmap.getHeight()));
                    });

            // Invalidate seekbar length and position
            mSeekBar.setMax((int) playerState.track.duration);
            mTrackProgressBar.setDuration(playerState.track.duration);
            mTrackProgressBar.update(playerState.playbackPosition);

            mSeekBar.setEnabled(true);
        }
    };

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        setTheme(R.style.AppTheme);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.app_remote_layout);

        mConnectButton = findViewById(R.id.connect_button);
        mConnectAuthorizeButton = findViewById(R.id.connect_authorize_button);
        mPlayerContextButton = findViewById(R.id.current_context_label);
        mSubscribeToPlayerContextButton = findViewById(R.id.subscribe_to_player_context_button);
        mCoverArtImageView = findViewById(R.id.image);
        mImageLabel = findViewById(R.id.image_label);
        mImageScaleTypeLabel = findViewById(R.id.image_scale_type_label);
        mPlayerStateButton = findViewById(R.id.current_track_label);
        mSubscribeToPlayerStateButton = findViewById(R.id.subscribe_to_player_state_button);
        mPlaybackSpeedButton = findViewById(R.id.playback_speed_button);
        mToggleRepeatButton = findViewById(R.id.toggle_repeat_button);
        mToggleShuffleButton = findViewById(R.id.toggle_shuffle_button);
        mPlayPauseButton = findViewById(R.id.play_pause_button);

        mSeekBar = findViewById(R.id.seek_to);
        mSeekBar.setEnabled(false);
        mSeekBar.getProgressDrawable().setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_ATOP);
        mSeekBar.getIndeterminateDrawable().setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_ATOP);

        mTrackProgressBar = new TrackProgressBar(mSeekBar);

        mViews = Arrays.asList(
                findViewById(R.id.disconnect_button),
                mSubscribeToPlayerContextButton,
                mSubscribeToPlayerStateButton,
                mImageLabel,
                mImageScaleTypeLabel,
                mPlayPauseButton,
                findViewById(R.id.seek_forward_button),
                findViewById(R.id.seek_back_button),
                findViewById(R.id.skip_prev_button),
                findViewById(R.id.skip_next_button),
                mToggleRepeatButton,
                mToggleShuffleButton,
                findViewById(R.id.connect_switch_to_local),
                findViewById(R.id.play_podcast_button),
                findViewById(R.id.play_track_button),
                findViewById(R.id.play_album_button),
                findViewById(R.id.play_artist_button),
                findViewById(R.id.play_playlist_button),
                findViewById(R.id.subscribe_to_capabilities),
                findViewById(R.id.get_collection_state),
                findViewById(R.id.remove_uri),
                findViewById(R.id.save_uri),
                findViewById(R.id.get_fitness_recommended_items_button),
                findViewById(R.id.echo),
                mSeekBar);

        SpotifyAppRemote.setDebugMode(true);

        onDisconnected();
        onConnectAndAuthorizedClicked(null);
    }

    @Override
    protected void onStop() {
        super.onStop();
        SpotifyAppRemote.disconnect(mSpotifyAppRemote);
        onDisconnected();
    }

    private void onConnected() {
        for (View input : mViews) {
            input.setEnabled(true);
        }
        mConnectButton.setEnabled(false);
        mConnectButton.setText(R.string.connected);
        mConnectAuthorizeButton.setEnabled(false);
        mConnectAuthorizeButton.setText(R.string.connected);

        onSubscribedToPlayerStateButtonClicked(null);
        onSubscribedToPlayerContextButtonClicked(null);
    }

    private void onConnecting() {
        mConnectButton.setEnabled(false);
        mConnectButton.setText(R.string.connecting);
        mConnectAuthorizeButton.setEnabled(false);
        mConnectAuthorizeButton.setText(R.string.connecting);
    }

    private void onDisconnected() {
        for (View view : mViews) {
            view.setEnabled(false);
        }
        mConnectButton.setEnabled(true);
        mConnectButton.setText(R.string.connect);
        mConnectAuthorizeButton.setEnabled(true);
        mConnectAuthorizeButton.setText(R.string.authorize);
        mCoverArtImageView.setImageResource(R.drawable.widget_placeholder);
        mPlayerContextButton.setText(R.string.title_player_context);
        mPlayerStateButton.setText(R.string.title_current_track);
        mToggleRepeatButton.clearColorFilter();
        mToggleRepeatButton.setImageResource(R.drawable.btn_repeat);
        mToggleShuffleButton.clearColorFilter();
        mToggleShuffleButton.setImageResource(R.drawable.btn_shuffle);
        mPlayerContextButton.setVisibility(View.INVISIBLE);
        mSubscribeToPlayerContextButton.setVisibility(View.VISIBLE);
        mPlayerStateButton.setVisibility(View.INVISIBLE);
        mSubscribeToPlayerStateButton.setVisibility(View.VISIBLE);
    }

    public void onConnectClicked(View v) {
        onConnecting();
        connect(false);
    }

    public void onConnectAndAuthorizedClicked(View view) {
        onConnecting();
        connect(true);
    }

    private void connect(boolean showAuthView) {

        SpotifyAppRemote.disconnect(mSpotifyAppRemote);

        SpotifyAppRemote.connect(
                getApplication(),
                new ConnectionParams.Builder(CLIENT_ID)
                        .setRedirectUri(REDIRECT_URI)
                        .showAuthView(showAuthView)
                        .build(),
                new Connector.ConnectionListener() {
                    @Override
                    public void onConnected(SpotifyAppRemote spotifyAppRemote) {
                        mSpotifyAppRemote = spotifyAppRemote;
                        RemotePlayerActivity.this.onConnected();
                    }

                    @Override
                    public void onFailure(Throwable error) {
                        if (error instanceof SpotifyRemoteServiceException) {
                            if (error.getCause() instanceof SecurityException) {
                                logError(error, "SecurityException");
                            } else if (error.getCause() instanceof IllegalStateException) {
                                logError(error, "IllegalStateException");
                            }
                        } else if (error instanceof NotLoggedInException) {
                            logError(error, "NotLoggedInException");
                        } else if (error instanceof AuthenticationFailedException) {
                            logError(error, "AuthenticationFailedException");
                        } else if (error instanceof CouldNotFindSpotifyApp) {
                            logError(error, "CouldNotFindSpotifyApp");
                        } else if (error instanceof LoggedOutException) {
                            logError(error, "LoggedOutException");
                        } else if (error instanceof OfflineModeException) {
                            logError(error, "OfflineModeException");
                        } else if (error instanceof UserNotAuthorizedException) {
                            logError(error, "UserNotAuthorizedException");
                        } else if (error instanceof UnsupportedFeatureVersionException) {
                            logError(error, "UnsupportedFeatureVersionException");
                        } else if (error instanceof SpotifyDisconnectedException) {
                            logError(error, "SpotifyDisconnectedException");
                        } else if (error instanceof SpotifyConnectionTerminatedException) {
                            logError(error, "SpotifyConnectionTerminatedException");
                        } else {
                            logError(error, String.format("Connection failed: %s", error));
                        }
                        RemotePlayerActivity.this.onDisconnected();
                    }
                });
    }

    public void onDisconnectClicked(View v) {
        SpotifyAppRemote.disconnect(mSpotifyAppRemote);
        onDisconnected();
    }

    public void onImageClicked(View view) {
        if (mSpotifyAppRemote != null) {
            mSpotifyAppRemote.getPlayerApi()
                    .getPlayerState()
                    .setResultCallback(playerState -> {
                        PopupMenu menu = new PopupMenu(this, view);

                        menu.getMenu().add(720, 720, 0, "Large (720px)");
                        menu.getMenu().add(480, 480, 1, "Medium (480px)");
                        menu.getMenu().add(360, 360, 2, "Small (360px)");
                        menu.getMenu().add(240, 240, 3, "X Small (240px)");
                        menu.getMenu().add(144, 144, 4, "Thumbnail (144px)");

                        menu.show();

                        menu.setOnMenuItemClickListener(item -> {
                            mSpotifyAppRemote.getImagesApi()
                                    .getImage(playerState.track.imageUri, Image.Dimension.values()[item.getOrder()])
                                    .setResultCallback(bitmap -> {
                                        mCoverArtImageView.setImageBitmap(bitmap);
                                        mImageLabel.setText(String.format(Locale.ENGLISH, "%d x %d", bitmap.getWidth(), bitmap.getHeight()));
                                    });
                            return false;
                        });
                    })
                    .setErrorCallback(mErrorCallback);
        }
    }

    public void onImageScaleTypeClicked(View view) {
        if (mSpotifyAppRemote != null) {
            mSpotifyAppRemote.getPlayerApi()
                    .getPlayerState()
                    .setResultCallback(playerState -> {
                        PopupMenu menu = new PopupMenu(this, view);

                        menu.getMenu().add(0, ImageView.ScaleType.CENTER.ordinal(), 0, "CENTER");
                        menu.getMenu().add(1, ImageView.ScaleType.CENTER_CROP.ordinal(), 1, "CENTER_CROP");
                        menu.getMenu().add(2, ImageView.ScaleType.CENTER_INSIDE.ordinal(), 2, "CENTER_INSIDE");
                        menu.getMenu().add(3, ImageView.ScaleType.MATRIX.ordinal(), 3, "MATRIX");
                        menu.getMenu().add(4, ImageView.ScaleType.FIT_CENTER.ordinal(), 4, "FIT_CENTER");
                        menu.getMenu().add(4, ImageView.ScaleType.FIT_XY.ordinal(), 5, "FIT_XY");

                        menu.show();

                        menu.setOnMenuItemClickListener(item -> {
                            mCoverArtImageView.setScaleType(ImageView.ScaleType.values()[item.getItemId()]);
                            mImageScaleTypeLabel.setText(ImageView.ScaleType.values()[item.getItemId()].toString());
                            return false;
                        });
                    })
                    .setErrorCallback(mErrorCallback);
        }
    }

    public void onPlayPodcastButtonClicked(View view) {
        playUri(PODCAST_URI);
    }

    public void onPlayTrackButtonClicked(View view) {
        playUri(TRACK_URI);
    }

    public void onPlayAlbumButtonClicked(View view) {
        playUri(ALBUM_URI);
    }

    public void onPlayArtistButtonClicked(View view) {
        playUri(ARTIST_URI);
    }

    public void onPlayPlaylistButtonClicked(View view) {
        playUri(PLAYLIST_URI);
    }

    private void playUri(String uri) {
        mSpotifyAppRemote.getPlayerApi()
                .play(uri)
                .setResultCallback(empty -> logMessage("Play successful"))
                .setErrorCallback(mErrorCallback);
    }

    public void showCurrentPlayerContext(View view) {
        if (view.getTag() != null) {
            showDialog("PlayerContext", gson.toJson(view.getTag()));
        }
    }

    public void showCurrentPlayerState(View view) {
        if (view.getTag() != null) {
            showDialog("PlayerState", gson.toJson(view.getTag()));
        }
    }

    public void onToggleShuffleButtonClicked(View view) {
        mSpotifyAppRemote.getPlayerApi()
                .toggleShuffle()
                .setResultCallback(empty -> logMessage("Toggle shuffle successful"))
                .setErrorCallback(mErrorCallback);
    }

    public void onToggleRepeatButtonClicked(View view) {
        mSpotifyAppRemote.getPlayerApi()
                .toggleRepeat()
                .setResultCallback(empty -> logMessage("Toggle repeat successful"))
                .setErrorCallback(mErrorCallback);
    }

    public void onSetShuffleTrueButtonClicked(View view) {
        mSpotifyAppRemote.getPlayerApi()
                .setShuffle(true)
                .setResultCallback(empty -> logMessage("Set shuffle true successful"))
                .setErrorCallback(mErrorCallback);
    }

    public void onSetRepeatAllButtonClicked(View view) {
        mSpotifyAppRemote.getPlayerApi()
                .setRepeat(Repeat.ALL)
                .setResultCallback(empty -> logMessage("Set repeat ALL successful"))
                .setErrorCallback(mErrorCallback);
    }

    public void onSkipPreviousButtonClicked(View view) {
        mSpotifyAppRemote.getPlayerApi()
                .skipPrevious()
                .setResultCallback(empty -> logMessage("Skip previous successful"))
                .setErrorCallback(mErrorCallback);
    }

    public void onPlayPauseButtonClicked(View view) {
        mSpotifyAppRemote.getPlayerApi().getPlayerState().setResultCallback(playerState -> {
            if (playerState.isPaused) {
                mSpotifyAppRemote.getPlayerApi()
                        .resume()
                        .setResultCallback(empty -> logMessage("Play current track successful"))
                        .setErrorCallback(mErrorCallback);
            } else {
                mSpotifyAppRemote.getPlayerApi()
                        .pause()
                        .setResultCallback(empty -> logMessage("Pause successful"))
                        .setErrorCallback(mErrorCallback);
            }
        });
    }

    public void onSkipNextButtonClicked(View view) {
        mSpotifyAppRemote.getPlayerApi()
                .skipNext()
                .setResultCallback(data -> {
                    logMessage("Skip next successful");
                })
                .setErrorCallback(mErrorCallback);
    }

    public void onSeekBack(View view) {
        mSpotifyAppRemote.getPlayerApi()
                .seekToRelativePosition(-15000)
                .setResultCallback(data -> {
                    logMessage("Seek back 15 sec successful");
                })
                .setErrorCallback(mErrorCallback);
    }

    public void onSeekForward(View view) {
        mSpotifyAppRemote.getPlayerApi()
                .seekToRelativePosition(15000)
                .setResultCallback(data -> {
                    logMessage("Seek forward 15 sec successful");
                })
                .setErrorCallback(mErrorCallback);
    }

    @SuppressLint("SetTextI18n")
    public void onSubscribeToCapabilities(View view) {

        if (mCapabilitiesSubscription != null && !mCapabilitiesSubscription.isCanceled()) {
            mCapabilitiesSubscription.cancel();
            mCapabilitiesSubscription = null;
        }

        mCapabilitiesSubscription = (Subscription<Capabilities>) mSpotifyAppRemote.getUserApi()
                .subscribeToCapabilities()
                .setEventCallback(capabilities -> logMessage(String.format("Can play on demand: %s", capabilities.canPlayOnDemand)))
                .setErrorCallback(mErrorCallback);

        mSpotifyAppRemote.getUserApi()
                .getCapabilities()
                .setResultCallback(capabilities -> logMessage(String.format("Can play on demand: %s", capabilities.canPlayOnDemand)))
                .setErrorCallback(mErrorCallback);
    }

    public void onGetCollectionState(View view) {
        mSpotifyAppRemote.getUserApi()
                .getLibraryState(TRACK_URI)
                .setResultCallback(libraryState -> logMessage(String.format(
                        "Item is in collection: %s\nCan be added to collection: %s",
                        libraryState.isAdded,
                        libraryState.canAdd
                )))
                .setErrorCallback(t -> logError(t, "Error:" + t.getMessage()));
    }

    public void onRemoveUri(View view) {
        mSpotifyAppRemote.getUserApi()
                .removeFromLibrary(TRACK_URI)
                .setResultCallback(empty -> logMessage("Remove from collection successful"))
                .setErrorCallback(throwable -> logError(throwable, "Error:" + throwable.getMessage()));
    }

    public void onSaveUri(View view) {
        mSpotifyAppRemote.getUserApi()
                .addToLibrary(TRACK_URI)
                .setResultCallback(empty -> logMessage("Add to collection successful"))
                .setErrorCallback(throwable -> logError(throwable, "Error:" + throwable.getMessage()));
    }

    public void onGetFitnessRecommendedContentItems(View view) {
        mSpotifyAppRemote.getContentApi()
                .getRecommendedContentItems(ContentApi.ContentType.FITNESS)
                .setResultCallback(listItems -> mSpotifyAppRemote.getContentApi()
                        .getChildrenOfItem(listItems.items[0], 3, 0)
                        .setResultCallback(childListItems -> {
                            showDialog("RecommendedContentItems", gson.toJson(childListItems));
                            ListItem item = null;
                            for (int i = 0; i < childListItems.items.length; ++i) {
                                item = childListItems.items[i];
                                if (item.playable) {
                                    logMessage(String.format("Trying to play %s", item.title));
                                    break;
                                } else {
                                    item = null;
                                }
                            }
                        })
                        .setErrorCallback(mErrorCallback)).setErrorCallback(mErrorCallback);
    }

    public void onConnectSwitchToLocal(View view){
        mSpotifyAppRemote.getConnectApi()
                .connectSwitchToLocalDevice()
                .setResultCallback(empty -> logMessage("Success!"))
                .setErrorCallback(mErrorCallback);
    }

    public void onSubscribedToPlayerContextButtonClicked(View view) {
        if (mPlayerContextSubscription != null && !mPlayerContextSubscription.isCanceled()) {
            mPlayerContextSubscription.cancel();
            mPlayerContextSubscription = null;
        }

        mPlayerContextButton.setVisibility(View.VISIBLE);
        mSubscribeToPlayerContextButton.setVisibility(View.INVISIBLE);

        mPlayerContextSubscription = (Subscription<PlayerContext>) mSpotifyAppRemote.getPlayerApi()
                .subscribeToPlayerContext()
                .setEventCallback(mPlayerContextEventCallback)
                .setErrorCallback(throwable -> {
                    mPlayerContextButton.setVisibility(View.INVISIBLE);
                    mSubscribeToPlayerContextButton.setVisibility(View.VISIBLE);
                    logError(throwable, "Subscribed to PlayerContext failed!");
                });
    }

    public void onSubscribedToPlayerStateButtonClicked(View view) {

        if (mPlayerStateSubscription != null && !mPlayerStateSubscription.isCanceled()) {
            mPlayerStateSubscription.cancel();
            mPlayerStateSubscription = null;
        }

        mPlayerStateButton.setVisibility(View.VISIBLE);
        mSubscribeToPlayerStateButton.setVisibility(View.INVISIBLE);

        mPlayerStateSubscription = (Subscription<PlayerState>) mSpotifyAppRemote.getPlayerApi()
                .subscribeToPlayerState()
                .setEventCallback(mPlayerStateEventCallback)
                .setLifecycleCallback(new Subscription.LifecycleCallback() {
                    @Override
                    public void onStart() {
                        logMessage("Event: start");
                    }

                    @Override
                    public void onStop() {
                        logMessage("Event: end");
                    }
                })
                .setErrorCallback(throwable -> {
                    mPlayerStateButton.setVisibility(View.INVISIBLE);
                    mSubscribeToPlayerStateButton.setVisibility(View.VISIBLE);
                    logError(throwable, "Subscribed to PlayerContext failed!");
                });
    }

    public void onEcho(View view) {
        mSpotifyAppRemote
                .call("com.spotify.echo", new Echo.Request("Hodor!"), Echo.Response.class)
                .setResultCallback(data -> logMessage(String.format("Echo to 'Hodor!' is '%s'", data.response)))
                .setErrorCallback(mErrorCallback);
    }

    private void logError(Throwable throwable, String msg) {
        Toast.makeText(this, "Error: " + msg, Toast.LENGTH_SHORT).show();
        Log.e(TAG, msg, throwable);
    }

    private void logMessage(String msg) {
        logMessage(msg, Toast.LENGTH_SHORT);
    }

    private void logMessage(String msg, int duration) {
        Toast.makeText(this, msg, duration).show();
        Log.d(TAG, msg);
    }

    private void showDialog(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .create()
                .show();
    }

    public void onPlaybackSpeedButtonClicked(View view) {
        PopupMenu menu = new PopupMenu(this, view);

        menu.getMenu().add(50, 50, 0, "0.5x");
        menu.getMenu().add(80, 80, 1, "0.8x");
        menu.getMenu().add(100, 100, 2, "1x");
        menu.getMenu().add(120, 120, 3, "1.2x");
        menu.getMenu().add(150, 150, 4, "1.5x");
        menu.getMenu().add(200, 200, 5, "2x");
        menu.getMenu().add(300, 300, 6, "3x");

        menu.show();

        menu.setOnMenuItemClickListener(item -> {
            mSpotifyAppRemote.getPlayerApi()
                    .setPodcastPlaybackSpeed(PlaybackSpeed.PodcastPlaybackSpeed.values()[item.getOrder()])
                    .setResultCallback(empty -> logMessage("Play podcast successful"))
                    .setErrorCallback(mErrorCallback);
            return false;
        });
    }

    private class TrackProgressBar {

        private static final int LOOP_DURATION = 500;
        private final SeekBar mSeekBar;
        private final Handler mHandler;


        private final SeekBar.OnSeekBarChangeListener mSeekBarChangeListener = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                mSpotifyAppRemote.getPlayerApi().seekTo(seekBar.getProgress())
                        .setErrorCallback(mErrorCallback);
            }
        };

        private final Runnable mSeekRunnable = new Runnable() {
            @Override
            public void run() {
                int progress = mSeekBar.getProgress();
                mSeekBar.setProgress(progress + LOOP_DURATION);
                mHandler.postDelayed(mSeekRunnable, LOOP_DURATION);
            }
        };

        private TrackProgressBar(SeekBar seekBar) {
            mSeekBar = seekBar;
            mSeekBar.setOnSeekBarChangeListener(mSeekBarChangeListener);
            mHandler = new Handler();
        }

        private void setDuration(long duration) {
            mSeekBar.setMax((int) duration);
        }

        private void update(long progress) {
            mSeekBar.setProgress((int) progress);
        }

        private void pause() {
            mHandler.removeCallbacks(mSeekRunnable);
        }

        private void unpause() {
            mHandler.removeCallbacks(mSeekRunnable);
            mHandler.postDelayed(mSeekRunnable, LOOP_DURATION);
        }
    }
}
