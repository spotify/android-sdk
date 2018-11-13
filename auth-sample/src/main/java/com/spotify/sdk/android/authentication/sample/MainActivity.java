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
import android.support.design.widget.Snackbar;
import android.support.design.widget.TextInputEditText;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.spotify.sdk.android.authentication.AuthenticationClient;
import com.spotify.sdk.android.authentication.AuthenticationRequest;
import com.spotify.sdk.android.authentication.AuthenticationResponse;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    public static final String CLIENT_ID = "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx";
    public static final int AUTH_CODE_REQUEST_CODE = 0x11;

    private final OkHttpClient mOkHttpClient = new OkHttpClient();
    private String mAccessToken;
    private String mAccessCode;
    private String mRefreshToken;
    private String mDeviceIp;
    private Call mCall;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //    getSupportActionBar().setTitle(String.format(
        //            Locale.US, "Spotify Auth Sample %s", com.spotify.sdk.android.authentication.BuildConfig.VERSION_NAME));
        getSupportActionBar().setTitle(String.format(
                Locale.US, "%s %s", getText(R.string.app_name), getText(R.string.app_version)));
    }

    @Override
    protected void onDestroy() {
        cancelCall();
        super.onDestroy();
    }

    public void onRequestCodeClicked(View view) {
        final AuthenticationRequest request = getAuthenticationRequest(AuthenticationResponse.Type.CODE);
        AuthenticationClient.openLoginActivity(this, AUTH_CODE_REQUEST_CODE, request);
    }

    // scopes: here not wanting to do streaming and the like, but do want to manipulate playlists etc.
    private AuthenticationRequest getAuthenticationRequest(AuthenticationResponse.Type type) {
        return new AuthenticationRequest.Builder(CLIENT_ID, type, getRedirectUri().toString())
                .setShowDialog(false)
                .setScopes(new String[]{"user-read-private user-read-email playlist-read-private playlist-read-collaborative playlist-modify-public playlist-modify-private"})
                .setCampaign("your-campaign-token")
                .build();
    }

    public void onRequestRefreshTokenClicked(View view) {
        if (mAccessCode == null) {
            final Snackbar snackbar = Snackbar.make(findViewById(R.id.activity_main), R.string.warning_need_code, Snackbar.LENGTH_SHORT);
            snackbar.getView().setBackgroundColor(ContextCompat.getColor(this, R.color.colorAccent));
            snackbar.show();
            return;
        }

        final RequestBody req_body = RequestBody.create(null, new byte[0]);

        final HttpUrl req_url = new HttpUrl.Builder()
                .scheme("https")
                .host("xxxxxxxxxxx.execute-api.us-east-1.amazonaws.com")
                .addPathSegment("test")
                .addPathSegment("swap")
                .addQueryParameter("code", mAccessCode)
                .build();

        final Request request = new Request.Builder()
                .url(req_url)
                .method("POST", req_body)
                .build();

        cancelCall();
        mCall = mOkHttpClient.newCall(request);

        mCall.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                setRefreshResponse("Failed to fetch data: " + e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    final JSONObject jsonObject = new JSONObject(response.body().string());
                    mRefreshToken = jsonObject.getString("refresh_token");
                    setRefreshResponse(getString(R.string.refresh_token, mRefreshToken));
                } catch (JSONException e) {
                    mRefreshToken = "";
                    setRefreshResponse("Failed to parse data: " + e);
                }
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        final AuthenticationResponse response = AuthenticationClient.getResponse(resultCode, data);

        if (AUTH_CODE_REQUEST_CODE == requestCode) {
            mAccessCode = response.getCode();
            updateCodeView();
        }
    }

    private void setRefreshResponse(final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                final TextView responseView = (TextView) findViewById(R.id.refresh_text_view);
                responseView.setText(text);
            }
        });
    }

    private void updateCodeView() {
        final TextView codeView = (TextView) findViewById(R.id.code_text_view);
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

    public void onSendRefreshTokenToDeviceClicked(View view) {
        final HttpUrl req_url = new HttpUrl.Builder()
                .scheme("https")
                .host(mDeviceIp)
                // Add device-specific stuff to the URL to send the refresh token to the device
                .build();

        final Request request = new Request.Builder()
                .url(req_url)
                .build();

        Log.i("Setting refresh token", request.toString());
        cancelCall();
        mCall = mOkHttpClient.newCall(request);

        mCall.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                final Snackbar snackbar = Snackbar.make(findViewById(R.id.activity_main), "Set refresh token I/O error", Snackbar.LENGTH_LONG);
                snackbar.show();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                final Snackbar snackbar = Snackbar.make(findViewById(R.id.activity_main),
                        response.code() == 200 ? "Set refresh token succeeded" : "Set refresh token failed", Snackbar.LENGTH_LONG);
                snackbar.show();
            }
        });

    }

    public void onIpAddressToggleClicked(View view) {
        ToggleButton ip_toggle = (ToggleButton)findViewById(R.id.ip_address_toggle);
        TextInputEditText text_input = (TextInputEditText)findViewById(R.id.edit_ip_input);

        if (ip_toggle.isChecked()) {
            text_input.setEnabled(true);
        } else {
            text_input.setEnabled(false);
            mDeviceIp = text_input.getText().toString();
        }
    }
}
