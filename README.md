
# Spotify Android SDK

The `Spotify Android SDK` allows your application to interact with the Spotify app service.
The capabilities of this SDK includes authentication and getting metadata for the currently playing track and context, issuing playback commands and initiating playback of tracks, albums or playlists.

The `Spotify Android SDK` consists of two libraries.<br/>
`Spotify Authentication Library` handles authentication flow and [Spotify Web API](https://developer.spotify.com/documentation/web-api/) calls and `Spotify App Remote` manages audio playback via the Spotify app.<br/>
The libraries work well together but can also be used separately. For example, if the application doesn't need to play music but needs user login or Web API capabilities it can use the `Spotify Authentication Library` by itself.

Head over to [Spotify for Developers](https://developer.spotify.com/documentation/android/) for more reading about the Android SDK.

### Spotify Authentication Library

This library provides a way to obtain OAuth access tokens that can subsequently be used to play music or used in calls to the [Spotify Web API](https://developer.spotify.com/web-api/).<br/>
[Spotify Authentication Library](https://github.com/spotify/android-auth) is an open source project.

[Spotify Authentication Library README](auth-lib/README.md)

### Spotify App Remote

This library contains classes for music playback control and metadata access.

[Spotify App Remote README](app-remote-lib/README.md)

## Getting Started

Walk through the quick start documentation on [Spotify for Developers](https://developer.spotify.com/documentation/android/quick-start).<br/>
Run the sample code in [app-remote-sample](app-remote-sample) and [auth-sample](auth-sample) modules.<br/>
Add the libraries as module dependencies to your project.

## License

```
Copyright © 2018 Spotify AB.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0
Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

### This project:

This project only modifies 3 files in the auth-sample directory (apart from this file!).

This is a bit of a late night hack to get a refresh token instead of an access token and send it to a device. So don't criticise the code too much, I was very tired when I wrote it and I do not know Android or Java at all. It does work though!

The device can then use the refresh token in order to keep being able to get an access token to access the Spotify web API.

In the Spotify Dev Console, you need to add your app, including the SHA1 signing key (in Android Studio, you can get this by clicking on the gradle thing on the right hand side, then go to spotify-sdk -> auth-sample -> Tasks -> android -> signingReport) and the package name (I used com.spotify.sdk.android.authentication.sample, maybe you can put anything here, don't know). The SHA1 key is definitely important, the whole thing doesn't work unless the SHA1 key matches (amongst all other things that stop it from working).

You'll need to implement a server that does the swap function (which returns a refresh token given an authorisation code) and refresh function (that returns a new access token when given a refresh token), as per the Spotify SDK. I did this in AWS using npm 8.1 based lambda functions. This is all explained in the Spotify SDK. The server knows the Client Id for your spotify app, the Client Secret and the redirect uri (in my case testschema://callback, same as in the spotify example code).

And so when you've done all this, you can run this app on your phone and :
1. Tap the Request Auth Code button. This gets an authorization code from spotify. This will display the spotify login screen etc if you're not logged in to spotify.
2. Tap the Request Refresh Token button. This gets a refresh token from spotify.
3. Edit the device IP address, then click the same button which will have changed to "Save the device IP address", tap the button again to save the address.
4. Tap the Send Refresh Token To Device button.

In the code you'll need to change a few bits where it says 'xxxxxxxx' and in particular you'll need to change line 185 of MainActivity.java where you need to add your device-specific bit for sending the refresh token to your device.

After your device has got the refresh token, it can send it to your refresh API to get a new access token when required (access token is only valid for 1 hour). The refresh tokens never seem to expire although I haven't tested this, maybe they do!


