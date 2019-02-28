
# Spotify Android SDK

The `Spotify Android SDK` allows your application to interact with the Spotify app service.
The capabilities of this SDK includes authentication and getting metadata for the currently playing track and context, issuing playback commands and initiating playback of tracks, albums or playlists.

**The `Spotify Android SDK` consists of two libraries**

`Spotify App Remote` manages audio playback via the [Spotify Music app](https://play.google.com/store/apps/details?id=com.spotify.music) using `app-remote-control` only.<br/>
`Spotify Authentication Library` handles authentication flow and [Spotify Web API](https://developer.spotify.com/documentation/web-api/) calls.

The libraries works well together but can also be used separately. For example, if the application doesn't need to play music but needs user login or Web API capabilities with other [scopes](https://developer.spotify.com/documentation/general/guides/scopes/) you can use the `Spotify Authentication Library` by itself.

Head over to [Spotify for Developers](https://developer.spotify.com/documentation/android/) for more reading about the Android SDK.

### Spotify App Remote

This library contains classes for music playback control and metadata access.
The built-in authentication in App Remote requests for `app-control-scope` only, and is the only scope needed to use App Remote. No token exchange. If more [scopes](https://developer.spotify.com/documentation/general/guides/scopes/) are needed for web-api related calls then use `Spotify Authentication Library`.

[Spotify App Remote README](app-remote-lib/README.md)

### Spotify Authentication Library

This library provides a way to obtain OAuth access tokens that can subsequently be used to play music or used in calls to the [Spotify Web API](https://developer.spotify.com/web-api/).<br/>
[Spotify Authentication Library](https://github.com/spotify/android-auth) is an open source project.

[Spotify Authentication Library README](auth-lib/README.md)

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
