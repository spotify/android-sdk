# Version 0.6.1

**What's New**

* Fix for adding uri to play queue via `PlayerApi.queue` command.
* Added `SpotifyRemoteServiceException` to catch `SecurityException` or `IllegalStateException` connection crash when trying to invoke startService/startForegroundService, see [ERRORS.md](ERRORS.md).
* MotionStateApi has been removed.

# Version 0.6.0

**What's New**

* Podcast playback support
* Set playback speed during Podcast playback
* Seek to relative playback position
* Improve image quality
* Bug fixes & improvements

# Version 0.5.0
  
**What's New**  
  
* Play music on the alarm stream
* Audio focusing & volume controls of the alarm stream
* Added connect and disconnect helper methods in `SpotifyAppRemote`
* Added toggle repeat & shuffle methods
* Fix URI validation
* Bug fixes & improvements

# Version 0.4.0   
  
**What's New**  
  
* Initial release