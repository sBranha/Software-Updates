# NewsWire — Cross-chat development handoff

> **Canonical source of truth:** GitHub, not a previous ChatGPT conversation.
> The assistant handling the next chat MUST inspect the current state of this branch, its commits, GitHub Actions and relevant source files before making new changes. This note documents continuity, not a claim that unfinished tasks are complete.

## Project and current state
- Repository: `sBranha/Software-Updates`
- Active development branch: `newswire-area-refresh-v0.6.5`
- Android application source: `newswave-android/`
- Android applicationId: `com.newswire.live`
- Current source version: `0.6.5`, versionCode `29`; previous completed feature release: `0.5.7`, code 21
- Build workflow: `.github/workflows/build-newswire-car-fix.yml`
- Current work is **Performance & Reliability**, preserving all features from v0.5.7.

## Development already present on the active branch
Recent branch commits include:
- Good Morning and My Day refresh error handling, recoverable stalled refreshes, progress and timestamps
- Local NWS alert prioritization and warning filtering; GPS rechecks while traveling
- Severe weather driving warnings for tornado, flash flood and severe thunderstorms
- Satellite Live Map zoom/recenter behavior; Android Auto map bitmap memory safety
- Automated source, map and regression checks in GitHub Actions
- A workflow update intended to produce an **installable test APK** in addition to the **unsigned Google Play intermediate AAB**
Do not reimplement these blindly; inspect the actual HEAD files and test before claiming they work.

## Core product requirements — don't lose these across chats
- Satellite imagery is default on **every** map. Keep road labels and relevant live overlays when possible.
- Location should use current GPS when available and saved ZIP as a reliable fallback, including while traveling.
- Official NWS weather alerts should never be represented as "no warnings" when the feed fails. Warnings need proper locations, times, and source attribution.
- Existing Storm Ahead driving service must remain explicitly opt-in, based on actual GPS heading/point checks, and must not falsely claim knowledge of a navigation route or guaranteed safety.
- Preserve Android Auto weather category compatibility; do not promise unsupported in-car audio or controls.
- Preserve all v0.5.7 screens: Live Satellite Map, Emergency Center, school sports, local-TV finder, NewsWire Radio on phone, Android widget, plus Good Morning, My Day, school notices, local events and traffic.
- User expects independent code changes, testing, packaging, GitHub publication, and one direct downloadable link for each build — do not make the user manually edit files or run tests.
- Be precise about compiled/tests vs live phone, road, provider or Android Auto checks.

## What to do next
1. Read the branch HEAD and recent commits rather than reverting to v0.5.7.
2. Inspect the latest GitHub Actions run for this branch: if successful, download and validate the test APK and AAB; if failed, read failed step logs and fix errors.
3. Verify versionCode 25 and the correct Android package name in deliverables.
4. **Critical signing caveat:** An available old keystore backup has been found to have a certificate mismatch with the key used for prior accepted releases. Do not sign with that wrong keystore or claim a Google Play-ready signed AAB. Locate and verify the accepted upload signing key/certificate before signing. If unavailable, deliver the verified unsigned AAB and clearly distinguish it from an upload-ready release; an installable test APK may be separately signed for testing if appropriate.
5. Inspect release behavior for refresh, GPS, official NWS data and Android Auto; do not claim physical-device tests you haven't performed.
6. Share clickable verified download links for every completed downloadable file, and summarize exactly what is done vs pending.

## Development workflow
Continue code edits on this branch using the connected GitHub tools. Run the full build workflow, inspect outcome, verify the exact artifact bytes, archive and package metadata, and only then deliver it. When a user asks "continue NewsWire", act on this repository instead of repeatedly asking them to paste previous chats. Never invent a successful build, Play publication or working download link.

_Last updated for chat continuity, 2026-10-23 local / 2026-10-24 UTC._

## Latest updater repair (v0.6.0 branch)
- Regular background Play Store updates remain controlled by Google Play auto-update settings; they do not require NewsWire to launch the in-app update flow.
- MainActivity previously only recognized AppUpdateType.IMMEDIATE and falsely told users they were up to date when flexible-only was offered. Fixed to prefer FLEXIBLE, fall back to IMMEDIATE, and show accurate no-update/unknown results.
- Handle completed flexible downloads with user-confirmed install, restore state after returning to app, and fall back to Play listing when an in-app flow isn't allowed.
- v0.5.9 is submitted by user and should not be overwritten by development branch. v0.6.0 is not published to Google Play.

## v0.6.1 widget repair
- Compact 2x2 widget footprint (120dp minWidth, 105dp minHeight); supports resizing.
- Simplified RemoteViews layout to render on smaller Samsung launcher grids while retaining weather, alerts, headlines, refresh and checked time.
- Safe initial render even if deferred WorkManager scheduling fails; handle restore and resize.
- Widget pin button checks request outcome and shows manual Home-screen path when launcher rejects it.
- Play production version code 25; debug test APK separate from installed Google Play package. Widget placement still needs a physical device/launcher check.


## v0.6.2 — Live Location & Widget Reliability
- On phone app's foreground My Area / News screens, a resolved GPS location may feed the widget. No GPS collection starts in widget or background worker. Foreground fix expires after 30 minutes and automatically falls back to saved ZIP.
- Cache keyed by the selected area prevents an out-of-order response or moved location from displaying weather/alerts for the wrong region.
- Widget displays a stale warning status after 60 minutes; on failed NWS fetch, never displays an all-clear. Manual refresh shows a progress state and handles failures.
- Dashboard and widget can share a single freshly retrieved NWS/local-news snapshot; saving a ZIP requests an update. 
- This branch's CI builds NewsWire v0.6.2 test APK and unsigned AAB. Do not assert device testing or Play publication without evidence.


## v0.6.2 added local live radio station streams
- NewsWire Radio retains the existing TextToSpeech local briefing.
- A separate on-demand Local AM/FM Stations section queries Radio Browser's live directory, prioritizing streams within 160 km of the passed phone GPS/saved ZIP location, with a state-matched fallback for stations lacking directory coordinates.
- Lists only streams the provider last verified and exposes HTTPS radio playback via Android MediaPlayer on the phone; playback stops when the screen closes, when switching stations, or when the spoken briefing starts.
- This is internet-streamed radio, not an FM tuner or Android Auto media app. Station listings, licensing, publisher site information and stream availability come from third parties; do not claim all real-world stations are listed.


## v0.6.3 — Radio favorites and playback recovery
- Search local results by station name, genre, region; favorites first or favorites-only. HTTPS stream bookmarks stay on-device and remain visible when directory is offline.
- Request/release audio focus and stop connections that do not finish preparing within 20 seconds.
- Preserve existing spoken briefing, widget and phone-only playback. No Android Auto media claims or background playback.
- CI compiles v0.6.3 versionCode 27 test APK and unsigned release AAB; signed Google Play bundle requires matching accepted upload certificate.


## v0.6.4 — Live TV & NewsWire Radio continuity
- Phone-only Live TV page linked from dashboard: verified broadcaster-owned official live news pages, national free services and geolocated north/central/south Alabama stations. In-app WebView playback attempted; official browser fallback when sites disallow WebView or require sign-in. No unlicensed transport URLs or circumvention.
- Save favorite TV channels locally; search by name or network, view All/Local/National/Saved.
- Fullscreen custom video handling; release player when closed; existing TV station finder remains available.
- Full radio search/favorites and all other v0.6.3 features retained.
- Use versionCode 28 and ensure signing certificate is the same as Google Play's accepted key before claiming signed AAB ready.


## v0.6.5 — My Area temperature refresh & Play upload issue
- User observed on phone My Area / Live that temperature refresh gives no feedback and could look stuck. Forced button always dispatches a fresh check with an active busy message and Updated at / last checked timestamps.
- Each refresh gets a monotonic generation token, so older same-location results cannot replace newer ones; 60-second timeout shows retry prompt.
- NWS station observation timestamp is displayed separately from fetch time, and station values older than two hours are not shown as current.
- "No active NWS alerts" only appears when the current check succeeded; unavailable warnings must remain explicitly unknown.
- Build v0.6.5 versionCode 29 (previous v0.6.4 code 28). An "already used" Play message might be a reused versionCode or a signing rejection, so check exact Play Console wording if rejected; matching signing key certificates alone does not prove Console acceptance.
