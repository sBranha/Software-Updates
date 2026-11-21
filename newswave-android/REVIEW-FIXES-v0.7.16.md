# NewsWire 0.7.16 — review repairs
Version code 51; package com.newswire.live. Preserves the supplied Claude UI/back/inset patch.

Changes: visible spoken-news controls; local/national briefing choice; stale-speech callback protection;
clearer timestamps, estimate wording and general school-sports headlines; periodic parked NWS checks
with fresh GPS and last-success status; actual foreground-service state; recent-location/saved-ZIP
choice; national news and official FAA ground-stop checks; corrected keyword and cooldown handling;
separate digest/important notification channels; HTTPS local WebView assets and renderer recovery;
Android Auto executor cleanup; checked Gradle wrapper and vendored/checksummed map dependencies.

Background notification checks are periodic (15-minute request; Android can delay), not instant push.
News RSS remains publisher-attributed Google News. The first successful check seeds existing stories
and FAA programs without announcing old backlog. An airport ground stop does not imply a nationwide stop.
Current FAA feed: https://nasstatus.faa.gov/api/airport-status-information

The Android Auto early-partner concern in the outside review is outdated: Google opened weather apps
to all developers in May 2025. Distribution review and actual Ford testing remain necessary.
https://developer.android.com/training/cars/whats-new
https://developer.android.com/about/versions/16/behavior-changes-16

This branch removes obsolete encrypted-backup retrieval workflows. It does not delete old public
release assets or old branch history. No private signing keys are committed.
No phone, Ford or Windows test has been claimed. Full compile/lint/browser evidence must be taken
from the successful run for the final branch commit.
