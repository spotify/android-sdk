Change Log
==========
## Version 2.0.1
* Removed unused code related to WebView

## Version 2.0.0
* Replaced WebView usage with Custom Tabs since Google and Facebook Login no longer support WebViews for authenticating users.
* Removed AuthorizationClient#clearCookies method from the API. Custom Tabs use the cookies from the browser.
* Bumped targetSdkVersion to 31

## Version 1.2.6
* Fixes an issue related to package visibility changes in API 30

## Version 1.2.5
* Updated targetSdkVersion (API 30), buildTools and Gradle plugin
* Removed unused jcenter repository
* Conform to package visibility restrictions when targeting Android 11 (API 30)

## Version 1.2.3
* Fixed a few issues with the webview based redirect

## Version 1.2.2
* Remove custom-tabs handling due to issues

## Version 1.2.1
* Fixes an issue that produced a redirect error when the redirect uri contains CAPS.

## Version 1.2.0

* Breaking changes: Rename classes from AuthenticationClassName to AuthorizationClassName
* Pass state parameter in AuthorizationResponse

2019-08-12

* Add  method to clear Spotify and Facebook cookies to AuthenticationClient
* Upgrade buildToolsVersion to 27.0.3
* Replace deprecated compile keyword in gradle files

## Version 1.1.0

_2018-02-28_

* Upgrade target and compile SDKs to 27
* Upgrade android support libraries to 27.0.2
