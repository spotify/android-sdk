/*
 * Copyright (c) 2015-2018 Spotify AB
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

package com.spotify.sdk.android.authentication.sample;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import com.google.android.material.snackbar.Snackbar;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AppCompatActivity;
import android.view.View;
import android.widget.TextView;

import com.spotify.sdk.android.auth.AuthorizationClient;
import com.spotify.sdk.android.auth.AuthorizationRequest;
import com.spotify.sdk.android.auth.AuthorizationResponse;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    public static final String CLIENT_ID = "089d841ccc194c10a77afad9e1c11d54";
    public static final int AUTH_TOKEN_REQUEST_CODE = 0x10;
    public static final int AUTH_CODE_REQUEST_CODE = 0x11;

    private final OkHttpClient mOkHttpClient = new OkHttpClient();
    private String mAccessToken;
    private String mAccessCode;
    private Call mCall;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getSupportActionBar().setTitle(String.format(
                Locale.US, "Spotify Auth Sample %s", com.spotify.sdk.android.auth.BuildConfig.LIB_VERSION_NAME));
    }

    @Override
    protected void onDestroy() {
        cancelCall();
        super.onDestroy();
    }

    public void onGetUserProfileClicked(View view) {
        if (mAccessToken == null) {
            final Snackbar snackbar = Snackbar.make(findViewById(R.id.activity_main), R.string.warning_need_token, Snackbar.LENGTH_SHORT);
            snackbar.getView().setBackgroundColor(ContextCompat.getColor(this, R.color.colorAccent));
            snackbar.show();
            return;
        }

        final Request request = new Request.Builder()
                .url("https://api.spotify.com/v1/me")
                .addHeader("Authorization","Bearer " + mAccessToken)
                .build();

        cancelCall();
        mCall = mOkHttpClient.newCall(request);

        mCall.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                setResponse("Failed to fetch data: " + e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    final JSONObject jsonObject = new JSONObject(response.body().string());
                    setResponse(jsonObject.toString(3));
                } catch (JSONException e) {
                    setResponse("Failed to parse data: " + e);
                }
            }
        });
    }

    public void onRequestCodeClicked(View view) {
        final AuthorizationRequest request = getAuthenticationRequest(AuthorizationResponse.Type.CODE);
        AuthorizationClient.openLoginActivity(this, AUTH_CODE_REQUEST_CODE, request);
    }

    public void onRequestTokenClicked(View view) {
        final AuthorizationRequest request = getAuthenticationRequest(AuthorizationResponse.Type.TOKEN);
        AuthorizationClient.openLoginActivity(this, AUTH_TOKEN_REQUEST_CODE, request);
    }

    private AuthorizationRequest getAuthenticationRequest(AuthorizationResponse.Type type) {
        return new AuthorizationRequest.Builder(CLIENT_ID, type, getRedirectUri().toString())
                .setShowDialog(false)
                .setScopes(new String[]{"user-read-email"})
                .setCampaign("your-campaign-token")
                .build();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        final AuthorizationResponse response = AuthorizationClient.getResponse(resultCode, data);
        if (response.getError() != null && !response.getError().isEmpty()) {
            setResponse(response.getError());
        }
        if (requestCode == AUTH_TOKEN_REQUEST_CODE) {
            mAccessToken = response.getAccessToken();
            updateTokenView();
        } else if (requestCode == AUTH_CODE_REQUEST_CODE) {
            mAccessCode = response.getCode();
            updateCodeView();
        }
    }

    private void setResponse(final String text) {
        runOnUiThread(() -> {
            final TextView responseView = findViewById(R.id.response_text_view);
            responseView.setText(text);
        });
    }

    private void updateTokenView() {
        final TextView tokenView = findViewById(R.id.token_text_view);
        tokenView.setText(getString(R.string.token, mAccessToken));
    }

    private void updateCodeView() {
        final TextView codeView = findViewById(R.id.code_text_view);
        codeView.setText(getString(R.string.code, mAccessCode));
    }

    private void cancelCall() {
        if (mCall != null) {
            mCall.cancel();
        }
    }

    private Uri getRedirectUri() {
        return new Uri.Builder()
                .scheme(getString(R.string.com_spotify_sdk_redirect_scheme))
                .authority(getString(R.string.com_spotify_sdk_redirect_host))
                .build();
    }
}
