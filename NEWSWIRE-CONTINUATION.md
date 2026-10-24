# NewsWire — Cross-chat development handoff

> **Canonical source of truth:** GitHub, not a previous ChatGPT conversation.
> The assistant handling the next chat MUST inspect the current state of this branch, its commits, GitHub Actions and relevant source files before making new changes. This note documents continuity, not a claim that unfinished tasks are complete.

## Project and current state
- Repository: `sBranha/Software-Updates`
- Active development branch: `newswire-widget-fix-v0.6.1`
- Android application source: `newswave-android/`
- Android applicationId: `com.newswire.live`
- Current source version: `0.6.1`, versionCode `25`; previous completed feature release: `0.5.7`, code 21
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
