# NewsWire 0.7.17 — Android Auto spoken news

Application ID: `com.newswire.live`. Version code: **52**. Target SDK: 36.

In the Ford Android Auto audio app list, choose **NewsWire Audio**, then **Play My News** or **National News**. Android Auto supplies Play/Pause, Stop and the playback timeline. The existing NewsWire Radar weather screen remains available. Connecting or browsing does not start audio by itself; Play commands from Android Auto, including host-requested resumption, are honored.

Play My News reads a short local briefing with NWS conditions/alerts and up to three attributed recent headlines. National News reads up to five attributed recent headlines without requiring a location. Local playback uses the recent location saved by the phone app, with the saved ZIP as fallback. Set your ZIP in the phone app before the first car test. The media service does not start background GPS tracking.

Briefings are synthesized to a temporary private audio file using Android Text-to-speech. Playback continues when the phone screen is off. Pause preserves the position. Navigation/call interruptions pause speech; transient audio focus returning resumes it unless you paused or stopped. A permanent focus loss requires Play. Losing the audio route or the last media-browser connection pauses playback so car speech does not unexpectedly continue on the phone speaker. Briefings older than 15 minutes refresh before resuming; a five-minute pause stops the session. Preparation has a 90-second timeout with a visible error. Stop, completion and destruction release playback, focus, pending work and private audio files.

Only authenticated media browsers receive the catalog/session. Android Auto's production certificate allowlist is derived from Google's Apache-2.0 UAMP sample; privileged system media controllers and this app are also accepted. Debug Android Auto signing certificates are excluded. No private signing material is in this repository.

## Build and verification

Use Java 21 (the Android 16 unit-test runtime requires it) and Android SDK platform 36/build tools 36.0.0. The checked-in Gradle 8.11.1 wrapper and dependency lockfile are required.

```
./newswave-android/gradlew -p newswave-android :app:testDebugUnitTest :app:assembleDebug :app:bundleRelease :app:lintRelease --no-daemon
```

The v0.7.17 GitHub workflow also runs the existing Java/JS/map browser regressions and the new car playback-state and bounded speech tests. Robolectric tests exercise the real media session/browser callback integration. Debug APKs use the separate `com.newswire.live.test` package. Release AABs from CI are unsigned; sign the Play bundle privately with the existing upload key.

## Google Play and Ford test

Upload the signed Google Play AAB to the existing internal-testing app. In Play Console, declare the new `mediaPlayback` foreground-service use as user-started spoken news playback, and include Android Auto media in the car distribution/review settings if prompted. Do not use the debug TEST.apk for your normal Play testing.

After updating from Google Play, park the Explorer and reconnect Android Auto. Choose NewsWire Audio in the audio app selector; tap Play My News. Test Pause/Resume, National News, navigation interruption, phone-screen-off playback and Stop. Verify reconnecting does not start audio. A phone install/build/test alone cannot establish Ford screen availability or real audio routing. No phone/Ford hardware result is claimed by automated tests.

## Data and attribution

Weather and alerts use NWS; headlines use the existing publisher-attributed Google News RSS. Spoken stories must have a publication timestamp within 24 hours. Feed failures say unavailable; estimates and observation/check times are named. NewsWire is not an emergency alert receiver. Android's configured TTS engine and voice determine speech availability and whether speech rendering uses a network connection.
