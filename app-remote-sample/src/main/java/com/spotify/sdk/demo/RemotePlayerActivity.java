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
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.spotify.android.appremote.api.ConnectionParams;
import com.spotify.android.appremote.api.Connector;
import com.spotify.android.appremote.api.ContentApi;
import com.spotify.android.appremote.api.SpotifyAppRemote;
import com.spotify.android.appremote.demo.R;
import com.spotify.protocol.client.ErrorCallback;
import com.spotify.protocol.client.Subscription;
import com.spotify.protocol.types.Capabilities;
import com.spotify.protocol.types.ListItem;
import com.spotify.protocol.types.PlayerContext;
import com.spotify.protocol.types.PlayerState;
import com.spotify.protocol.types.Repeat;
import com.spotify.protocol.types.Shuffle;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class RemotePlayerActivity extends FragmentActivity {

    private static final String TAG = RemotePlayerActivity.class.getSimpleName();

    private static final String CLIENT_ID = "089d841ccc194c10a77afad9e1c11d54";
    private static final String REDIRECT_URI = "comspotifytestsdk://callback";

    private static final String TRACK_URI = "spotify:track:1UBQ5GK8JaQjm5VbkBZY66";
    private static final String ALBUM_URI = "spotify:album:1x0uzT3ETlIYjPueTyNfnQ";
    private static final String ARTIST_URI = "spotify:artist:3WrFJ7ztbogyGnTHbHJFl2";
    private static final String PLAYLIST_URI = "spotify:user:spotify:playlist:0ck07VvqXYnuOhsZFy4fFe";

    private SpotifyAppRemote mSpotifyAppRemote;

    private TextView mPlayerStateView;
    private TextView mPlayerContextView;
    private TextView mRecentErrorView;
    private TextView mCapabilitiesView;
    private ImageView mImageView;

    private Button mConnect;
    private Button mToggleRepeatButton;
    private Button mToggleShuffleButton;

    private final ErrorCallback mErrorCallback = throwable -> logError(throwable, "Boom!");

    private TrackProgressBar mTrackProgressBar;

    private Subscription<PlayerState> mPlayerStateSubscription;
    private Subscription<PlayerContext> mPlayerContextSubscription;
    private Subscription<Capabilities> mCapabilitiesSubscription;

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

    @SuppressLint("SetTextI18n")
    private final Subscription.EventCallback<PlayerState> mPlayerStateEventCallback = new Subscription.EventCallback<PlayerState>() {
        @Override
        public void onEvent(PlayerState data) {
            mToggleRepeatButton.setText(getString(R.string.toggle_repeat_button) + " " + data.playbackOptions.repeatMode);
            mToggleShuffleButton.setText(getString(R.string.toggle_shuffle_button) + " " + data.playbackOptions.isShuffling);

            mPlayerStateView.setText(String.format(Locale.US, "%d:%s", System.currentTimeMillis(), data));

            if (data.playbackSpeed > 0) {
                mTrackProgressBar.unpause();
            } else {
                mTrackProgressBar.pause();
            }

            if (data.track != null) {

                mSpotifyAppRemote.getImagesApi()
                        .getImage(data.track.imageUri)
                        .setResultCallback(bitmap -> mImageView.setImageBitmap(bitmap));

                mTrackProgressBar.setDuration(data.track.duration);
                mTrackProgressBar.update(data.playbackPosition);

                mSeekBar.setEnabled(true);
            } else {
                mSeekBar.setEnabled(false);
            }
        }
    };

    @SuppressLint("SetTextI18n")
    private final Subscription.EventCallback<PlayerContext> mPlayerContextEventCallback = new Subscription.EventCallback<PlayerContext>() {
        @Override
        public void onEvent(PlayerContext data) {
            mPlayerContextView.setText(String.format(Locale.US, "%d:%s", System.currentTimeMillis(), data));
        }
    };

    private List<View> mViews;
    private SeekBar mSeekBar;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.remote_buttons);

        mConnect = findViewById(R.id.connect);
        mPlayerStateView = findViewById(R.id.current_track);
        mPlayerContextView = findViewById(R.id.current_context);
        mCapabilitiesView = findViewById(R.id.capabilities);
        mToggleShuffleButton = findViewById(R.id.toggle_shuffle_button);
        mToggleRepeatButton = findViewById(R.id.toggle_repeat_button);
        mRecentErrorView = findViewById(R.id.recent_error);
        mImageView = findViewById(R.id.image);

        mSeekBar = findViewById(R.id.seek_to);
        mSeekBar.setEnabled(false);

        EditText seekInput = findViewById(R.id.seek_input);
        seekInput.setOnEditorActionListener((textView, actionId, keyEvent) -> {
            boolean handled = false;
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                mSpotifyAppRemote.getPlayerApi().seekTo(Long.parseLong(textView.getText().toString()))
                        .setErrorCallback(mErrorCallback);
                handled = true;
            }
            return handled;
        });

        mTrackProgressBar = new TrackProgressBar(mSeekBar);

        mViews = Arrays.asList(
                findViewById(R.id.disconnect),
                findViewById(R.id.show_player_state_button),
                findViewById(R.id.play_track_button),
                findViewById(R.id.play_album_button),
                findViewById(R.id.play_artist_button),
                findViewById(R.id.play_playlist_button),
                findViewById(R.id.pause_button),
                findViewById(R.id.resume_button),
                findViewById(R.id.skip_prev_button),
                findViewById(R.id.skip_next_button),
                mToggleShuffleButton,
                mToggleRepeatButton,
                findViewById(R.id.set_shuffle_button),
                findViewById(R.id.set_repeat_button),
                findViewById(R.id.subscribe_to_capabilities),
                findViewById(R.id.get_collection_state),
                findViewById(R.id.remove_uri),
                findViewById(R.id.save_uri),
                findViewById(R.id.get_fitness_child),
                findViewById(R.id.connect_switch_to_local),
                findViewById(R.id.subscribe_to_player_state),
                findViewById(R.id.subscribe_to_player_context),
                findViewById(R.id.echo),
                mSeekBar,
                seekInput);

        SpotifyAppRemote.setDebugMode(true);

        onDisconnected();
    }

    @Override
    protected void onStop() {
        super.onStop();
        SpotifyAppRemote.disconnect(mSpotifyAppRemote);
    }

    private void onConnected() {
        for (View input : mViews) {
            input.setEnabled(true);
        }
        mConnect.setEnabled(false);
    }

    private void onDisconnected() {
        for (View view : mViews) {
            view.setEnabled(false);
        }
        mConnect.setEnabled(true);
        mImageView.setImageBitmap(null);
        mRecentErrorView.setText(null);
        mPlayerStateView.setText(null);
    }

    public void onConnectClicked(View v) {
        connect(false);
    }


    public void onConnectAndAuthorizedClicked(View view) {
        connect(true);
    }

    private void connect(boolean showAuthView) {
        final int imageSize = (int) getResources().getDimension(R.dimen.image_size);

        SpotifyAppRemote.disconnect(mSpotifyAppRemote);

        SpotifyAppRemote.connect(
                getApplication(),
                new ConnectionParams.Builder(CLIENT_ID)
                        .setRedirectUri(REDIRECT_URI)
                        .setPreferredImageSize(imageSize)
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
                        logMessage(String.format("Connection failed: %s", error));
                        RemotePlayerActivity.this.onDisconnected();
                    }
                });
    }

    public void onDisconnectClicked(View v) {
        SpotifyAppRemote.disconnect(mSpotifyAppRemote);
        onDisconnected();
    }

    public void onResumeButtonClicked(View view) {
        mSpotifyAppRemote.getPlayerApi()
                .resume()
                .setResultCallback(empty -> logMessage("Resume successful"))
                .setErrorCallback(mErrorCallback);
    }

    public void onPauseButtonClicked(View view) {
        mSpotifyAppRemote.getPlayerApi()
                .pause()
                .setResultCallback(empty -> logMessage("Pause successful"))
                .setErrorCallback(mErrorCallback);
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

    public void onShowPlayerStateButtonClicked(View view) {
        mSpotifyAppRemote.getPlayerApi()
                .getPlayerState()
                .setResultCallback(playerState -> logMessage("Got current track" + playerState))
                .setErrorCallback(mErrorCallback);
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
                .setResultCallback(empty -> logMessage("Toggle shuffle successful"))
                .setErrorCallback(mErrorCallback);
    }

    public void onSetRepeatAllButtonClicked(View view) {
        mSpotifyAppRemote.getPlayerApi()
                .setRepeat(Repeat.ALL)
                .setResultCallback(empty -> logMessage("Toggle repeat successful"))
                .setErrorCallback(mErrorCallback);
    }

    public void onSkipPreviousButtonClicked(View view) {
        mSpotifyAppRemote.getPlayerApi()
                .skipPrevious()
                .setResultCallback(empty -> logMessage("Skip previous successful"))
                .setErrorCallback(mErrorCallback);
    }

    public void onSkipNextButtonClicked(View view) {
        mSpotifyAppRemote.getPlayerApi()
                .skipNext()
                .setResultCallback(empty -> logMessage("Skip next successful"))
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
                .setEventCallback(capabilities -> mCapabilitiesView.setText("ON_DEMAND: " + capabilities.canPlayOnDemand))
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

    public void onGetFitnessChild(View view) {
        mSpotifyAppRemote.getContentApi()
                .getRecommendedContentItems(ContentApi.ContentType.FITNESS)
                .setResultCallback(listItems -> mSpotifyAppRemote.getContentApi()
                        .getChildrenOfItem(listItems.items[0], 3, 0)
                        .setResultCallback(childListItems -> {
                            logMessage("Got Items: " + childListItems);
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
                            mSpotifyAppRemote.getContentApi()
                                    .playContentItem(item)
                                    .setResultCallback(empty -> logMessage("Content item played!"))
                                    .setErrorCallback(mErrorCallback);
                        })
                        .setErrorCallback(mErrorCallback)).setErrorCallback(mErrorCallback);
    }

    public void onConnectSwitchToLocal(View view){
        mSpotifyAppRemote.getConnectApi()
                .connectSwitchToLocalDevice()
                .setResultCallback(empty -> logMessage("Success!"))
                .setErrorCallback(mErrorCallback);
    }

    public void onSubscribedToCurrentTrackButtonClicked(View view) {

        if (mPlayerStateSubscription != null && !mPlayerStateSubscription.isCanceled()) {
            mPlayerStateSubscription.cancel();
            mPlayerStateSubscription = null;
        }

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
                .setErrorCallback(mErrorCallback);
    }

    public void onSubscribedToPlayerContextButtonClicked(View view) {
        if (mPlayerContextSubscription != null && !mPlayerContextSubscription.isCanceled()) {
            mPlayerContextSubscription.cancel();
            mPlayerContextSubscription = null;
        }

        mPlayerContextSubscription = (Subscription<PlayerContext>) mSpotifyAppRemote.getPlayerApi()
                .subscribeToPlayerContext()
                .setEventCallback(mPlayerContextEventCallback)
                .setErrorCallback(mErrorCallback);
    }

    public void onEcho(View view) {
        mSpotifyAppRemote
                .call("com.spotify.echo", new Echo.Request("Hodor!"), Echo.Response.class)
                .setResultCallback(data -> logMessage(String.format("Echo to 'Hodor!' is '%s'", data.response)))
                .setErrorCallback(mErrorCallback);
    }

    private void logError(Throwable t, String msg) {
        Toast.makeText(this, "Error: " + msg, Toast.LENGTH_SHORT).show();
        Log.e(TAG, msg, t);
        mRecentErrorView.setText(String.valueOf(t));
    }

    private void logMessage(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        Log.d(TAG, msg);
    }

}
