# Gotify Android [![Build Status][github-action-badge]][github-action] [![FOSSA Status][fossa-badge]][fossa] [![latest release version][release-badge]][release] [![F-Droid][fdroid-badge]][fdroid]

<img align="right" src="app.gif" width="250" />

Gotify Android connects to [gotify/server](https://github.com/gotify/server) and shows push notifications on new messages.

## Features

* show push notifications on new messages
* view and delete messages
* local message and application caching (Room) for offline access
* automatic background sync: persists messages when received and on reconnect


### Disable battery optimization

By default Android kills long running apps as they drain the battery. With enabled battery optimization, Gotify will be killed and you wont receive any notifications.

Here is one way to disable battery optimization for Gotify.

* Open "Settings"
* Search for "Battery Optimization"
* Find "Gotify" and disable battery optimization

See also https://dontkillmyapp.com for phone manufacturer specific instructions to disable battery optimizations.

### Minimize the Gotify foreground notification

*Only possible for Android version >= 8*

The foreground notification showing the connection status can be manually minimized to be less intrusive:

* Open Settings -> Apps -> Gotify
* Click Notifications
* Click on `Gotify foreground notification`
* Toggle the "Minimize" option / Select a different "Behavior" or "Importance" (depends on your Android version)
* Restart Gotify

## Message Priorities

| Notification | Gotify Priority|
|- |-|
| - | 0 |
| Icon in notification bar | 1 - 3 |
| Icon in notification bar + Sound | 4 - 7 |
| Icon in notification bar + Sound + Vibration | 8 - 10 |

## Building

Use Java 17 and execute the following command to build the apk.

```bash
$ ./gradlew build
```

## Update client

* Run `./gradlew generateSwaggerCode`
* Delete `client/settings.gradle` (client is a gradle sub project and must not have a settings.gradle)
* Delete `repositories` block from `client/build.gradle`
* Delete `implementation "com.sun.xml.ws:jaxws-rt:x.x.x“` from `client/build.gradle`
* Insert missing bracket in `retryingIntercept` method of class `src/main/java/com/github/gotify/client/auth/OAuth`
* Commit changes

## Versioning
We use [SemVer](http://semver.org/) for versioning. For the versions available, see the
[tags on this repository](https://github.com/gotify/android/tags).

## License
This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details

## This fork / extra features

This repository contains a small fork of the original Gotify Android client with the following pragmatic additions aimed at improving offline usability:

- Local persistence: messages and applications are stored in a Room database so the UI can show cached content when there is no network.
- Instant offline UI: cached messages are loaded immediately on app start; network sync runs in the background and merges new messages without duplicating entries.
- Minimal behavioral change: when online the app still behaves like the original client; the caching layer is additive and only affects behavior when offline or when reconnecting.

Why this differs from the original
- The upstream client focuses on live push notifications. This fork keeps that behavior but adds a lightweight local cache to make the app usable when the device is offline or the server is temporarily unreachable.
- The changes are intentionally small and follow the project's existing patterns (Room, repository layer, and existing model classes) to keep maintenance and merging straightforward.

 [github-action-badge]: https://github.com/gotify/android/workflows/Build/badge.svg
 [github-action]: https://github.com/gotify/android/actions?query=workflow%3ABuild
 [playstore]: https://play.google.com/store/apps/details?id=com.github.gotify
 [fdroid-badge]: https://img.shields.io/f-droid/v/com.github.gotify.svg
 [fdroid]: https://f-droid.org/de/packages/com.github.gotify/
 [fossa-badge]: https://app.fossa.io/api/projects/git%2Bgithub.com%2Fgotify%2Fandroid.svg?type=shield
 [fossa]: https://app.fossa.io/projects/git%2Bgithub.com%2Fgotify%2Fandroid
 [release-badge]: https://img.shields.io/github/release/gotify/android.svg
 [release]: https://github.com/gotify/android/releases/latest
 
