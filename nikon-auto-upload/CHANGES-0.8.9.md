# Nikon Auto Upload 0.8.9 (build 34) and Camera Auto Upload for Windows 1.0.8

Starting point: the November 4 Android 0.8.7 (build 32) source and Windows 1.0.7 source from the review package (identical to `sBranha/Software-Updates` branch `nikon-auto-upload-build`, commit 32d1c73).

## Version numbers

- Android: **0.8.9, versionCode 34**. 0.8.8 (code 33) exists as an older comparison APK, so 34 keeps installs moving forward on any phone that ever had 0.8.8. None of 0.8.7's features were removed; 0.8.8's source was never recovered, so nothing from it could be merged.
- Windows: **1.0.8** (project, installer, window title and header now all read the same number; the title and log read it from the program itself).
- The Android version is now shown on the **Home screen under the camera name**, in the Settings footer, and in the exported diagnostics. It's read from the installed package, so it can't go stale again (the old footer said 0.8.1).

## Android: confirmed problems fixed

| # | Problem (file) | What went wrong | Fix |
|---|---|---|---|
| 1 | Notification flood (`DirectTransferService`) | Every photo posted a new notification and every FTP command updated the main one. Android rate-limits apps to about 5 notification updates/second and caps active ones at 50, so during a burst updates were dropped and the system slowed down. | One summary notification each for "received" and "uploaded", plus the status notification, all updated at most every 0.75 s. |
| 2 | Duplicate uploads (`DirectTransferService`) | The 30-second retry timer called the upload loop directly while it could also be running on the upload thread, so the same photo could upload twice. If Flickr accepted a photo but the follow-up check failed, the photo was uploaded again. | One scheduler, an in-flight list, and the Flickr photo id is saved the moment the upload succeeds. Later steps never re-upload. |
| 3 | One bad photo blocked everything (`DirectTransferService`) | The loop always stopped at the first failure, and it always visited photos in the same order, so a photo Flickr refused stopped every upload behind it, forever. | Refused photos are moved to "Failed" (Uploads ▸ Retry brings them back). Network errors back off from 5 s up to 5 min, and photos that keep failing go to the back of the line. |
| 4 | Uploads one at a time (`DirectTransferService`) | Each photo needed 5 Flickr round trips in a row, so a burst over cellular fell behind fast. | 3 uploads in parallel (oldest first). Album filing is serialized so parallel uploads can't create duplicate albums. |
| 5 | Too many Flickr calls (`FlickrClient`) | Each album add re-downloaded up to 1,500 album entries to "verify". Flickr allows 3,600 API calls an hour, which is roughly 720 photos an hour at that rate. | Flickr's own "ok" from addPhoto/create is the confirmation ("already in album" counts as success). Album list is paged past 500 albums. |
| 6 | Uploads held whole photo in memory (`FlickrClient`) | `HttpURLConnection` buffered the full multipart body (25+ MB per Z8 JPEG) before sending. | Streams with a fixed length; nothing large is held in memory. |
| 7 | Cellular request leak (`DirectTransferService`) | Every time cellular dropped, a new network request was registered without removing the old one. Android allows about 100, then throws, after which uploads stop for good. | The old request is always removed first. |
| 8 | No uploads without cellular (`DirectTransferService`) | Flickr traffic was forced onto cellular only. A phone with data off or no SIM never uploaded, even on home Wi-Fi. | Cellular still preferred (so the phone can stay on the camera's Wi-Fi). If there's none, any network Android has verified reaches the internet is used. The camera's Wi-Fi never qualifies. New switch in Settings ▸ Transfer Options. |
| 9 | Phone could doze mid-burst (`DirectTransferService`, manifest) | Only the screen was kept on, and only while the app was open. With the screen off, the CPU could sleep and Wi-Fi power-save throttled the camera link. | While the camera is connected, photos are saving, or uploads are running: CPU wake lock + Wi-Fi performance lock, released 2 minutes after activity stops. Added WAKE_LOCK permission. |
| 10 | FTP: one failed file ended the whole camera session (`SimpleFtpServer`) | Any network hiccup mid-file threw out of the session, left a stray `.part` file, and the camera had to reconnect and log in again. | Partial file is deleted, camera gets "426 resend", and the session stays open. |
| 11 | FTP: resumed upload could save a broken photo (`SimpleFtpServer`) | "REST" (resume at offset) was accepted but ignored, so a resumed upload would have saved only the tail of a photo as if complete. "APPE" was answered "OK" with no data handling. | Non-zero resume is refused so the camera sends the whole file; APPE refused. |
| 12 | FTP: SIZE always "not found" (`SimpleFtpServer`) | Cameras that verify an upload with SIZE were told the file didn't exist. | Reports the real size of files received in the session. |
| 13 | FTP: stop didn't stop (`SimpleFtpServer`) | Live camera sockets stayed open after "restart receiver". | All sockets closed on stop. |
| 14 | Receive thread did the card editor too (`DirectTransferService`) | The full-resolution sports card render ran on the same thread that saves incoming photos, delaying everything behind it. | Cards render on their own thread; receiving and uploading originals never wait on them. If a card fails and you chose "card only", the original is uploaded instead of nothing. |
| 15 | Card render ran out of memory (`OverlayProcessor`) | Allowed up to 50 MP: two 183 MB bitmaps for one Z8 frame. Most phones can't, so cards failed. | Cards render at up to 24 MP (6000×4000), stepping down to 12 MP if memory is still short. `largeHeap` enabled. Originals are untouched. |
| 16 | USB-C/Wi-Fi PTP name collisions (`UsbCameraActivity`, `NikonPtpAutoTransfer`) | Imports were staged by camera file name in the cache folder. Two DSC_0001.JPG (two folders/cards) overwrote each other while one was still queued, and Android can clear cache at any time. | Unique names in app storage, a size check, and recovery after restarts. |
| 17 | USB PTP read errors (`UsbPtpCamera`) | 12-byte header reads from a bulk endpoint fail when the camera sends a full USB packet, and a zero-length packet (about 1 in 512 files) was treated as "camera stopped responding". | All USB reads go through a buffer; zero-length packets handled. |
| 18 | USB re-imports after replug (`UsbPtpCamera`) | "Already imported" was keyed on object handles, which can change after reconnecting. | Keyed on storage + name + size + capture date (old keys still honored). |
| 19 | Wi-Fi PTP skipped failed photos forever (`NikonPtpAutoTransfer`) | A photo whose download failed once was marked "seen" and never retried. Event channel was never read and pings were answered on the wrong channel. | Up to 3 retries per photo; events drained, pings answered, and a new-photo event triggers an immediate check. |
| 20 | Rotating the phone could log you out of Flickr (`MainActivity`, `FlickrClient`) | After connecting, a rotation replayed the sign-in callback; the second exchange failed and wrote empty tokens. | Callback consumed once; stale callbacks ignored; empty replies never overwrite a login. |
| 21 | OAuth signing with `*` (`OAuth1`) | Titles/tags containing `*` produced an invalid signature. | Encoded per RFC 3986. |
| 22 | Photos tab froze after big bursts (`MainActivity`) | Up to 500 thumbnails were decoded on the UI thread every time Photos opened. | Thumbnails load in the background with a memory cache. |
| 23 | Stale "Camera hotspot RUNNING" (`MainActivity`) | After the app process was killed, Settings showed the hotspot running (with no Start button) though it was gone. | Shows the real state. |
| 24 | Status text "Receiving photo" never showed (`DirectTransferService`) | Matched on text the receiver never sends. | Camera-connected state now comes from the live FTP session count. |
| 25 | Partial phone copy (`DirectTransferService`) | Gallery copy wasn't size-checked. | Size verified before the photo is marked saved. |

## Windows: confirmed problems fixed

| # | Problem (file) | Fix |
|---|---|---|
| W1 | Camera waited for the editor and copy after every FTP file (`Services.cs`) | Processing runs in order in the background; the camera can send the next file immediately. |
| W2 | Failed FTP transfers left half files under the real photo name | Receives into a hidden `.part`, renamed only when complete; camera told to resend. |
| W3 | Tether-folder files could be picked up while still being written (and a 0-byte file was "ready" after 30 instant retries) | A file is ready only when the writer has closed it and its size is stable. |
| W4 | Folder watcher dropped events in bursts | Maximum change buffer, and an automatic rescan if Windows reports an overflow. |
| W5 | One failing photo blocked the whole Flickr queue; missing files sat in it forever | Per-item retry delay (15 s up to 15 min), others keep uploading; missing files removed. |
| W6 | Queue/settings file could be corrupted by a crash (and then silently reset to empty) | Written atomically. Queue saves are also locked against concurrent writes. |
| W7 | No LIST/NLST/MKD etc.; resume accepted but ignored | Added; non-zero resume refused. |
| W8 | Log grew forever and background threads waited on the UI | Non-blocking, trimmed log. |
| W9 | Version strings disagreed (1.0.2 / 1.0 / 1.0.7) | All 1.0.8, read from the program. |

Windows uploads are still always "Only me + hidden from search", as in 1.0.7 (there is no privacy setting in the Windows app).

## Build changes

- The committed source is now the finished, prepared source. Both GitHub workflows build it directly; the old `tools/patch_*.py`, `prepare_build.py` and `patch_windows_10x.py` chain is kept for history only and must not be re-run.
- Branch `claude/nikon-0.8.9` on `sBranha/Software-Updates`. That branch removes the unrelated David workflows so pushing it can't trigger them. **Don't merge it into main as-is.**
- The Android workflow produces an **unsigned** APK, as before. Sign it with your existing key, or it won't install over 0.8.7.

## Checks run

- Android: all Java sources compiled cleanly (javac) against the Android 15 framework before and after the changes.
- Android full build on GitHub Actions (branch `claude/nikon-0.8.9`, run 45904917288): **passed**, including Android's release lint check. Artifact: `CameraAutoUpload-0.8.9-UNSIGNED`.
- Windows build on GitHub Actions (run 45904917267): **passed** — compile, self-contained publish, startup smoke test (window opened, no crash), and Inno Setup installer. Artifact: `CameraAutoUpload-Windows-Setup-1.0.8`.
- Not done: signing the APK, running it on a phone, using the Windows app beyond the startup smoke test, any Z8 hardware test.

## Phone + Z8 test checklist

1. Install the signed 0.8.9 over 0.8.7. Home screen should say "Version 0.8.9 (build 34)". Your Flickr login, FTP login, albums and templates should still be there.
2. Settings ▸ Flickr Options: turn on "Only me". Start a game session named e.g. "Test".
3. Z8 FTP to the phone as before. Shoot a 20-frame burst. All 20 should appear in Photos, and Flickr should show 20 Only Me photos in the album "M-d-yy Test".
4. Long burst: shoot continuously until the Z8 buffer fills (or as long as you would in a game). Watch Uploads: "Uploading now" should show up to 3, and the queue should drain steadily. Note photos/minute and compare with the count on the card.
5. Screen off: start a burst, lock the phone, wait 5 minutes, unlock. Nothing should be stuck; counts should match.
6. Leave the camera idle for 15+ minutes, then shoot a burst. It should reconnect and receive without touching the phone.
7. Turn the Z8's Wi-Fi off mid-burst, then back on. Missing frames should be resent by the camera; no broken or half photos in the gallery.
8. Turn on airplane mode (keep Wi-Fi to the camera) for a burst, then turn it off. Photos save locally, then upload on their own. No duplicates on Flickr.
9. Force-stop the app with photos still queued, reopen. The queue should continue; no duplicates.
10. Rotate the phone on Home, Photos, Uploads and Settings, especially right after connecting Flickr. Flickr should stay connected.
11. Turn mobile data off, keep home Wi-Fi with internet: uploads should go over Wi-Fi (cellular status shows "Wi-Fi internet ready").
12. If you use cards: lock a layout and shoot a burst. Originals should arrive at full speed and cards follow behind.
13. USB-C: plug the Z8 in, Import New, unplug mid-transfer, replug, import again. No duplicates, no broken files.
14. Confirm nothing was deleted from the camera card, and phone copies stay unless you enabled auto-delete.

## Windows test checklist

1. Install 1.0.8 over 1.0.7. Title bar and header should say v1.0.8. Settings and queue should still be there.
2. FTP from the camera to the PC, shoot a burst. Every file appears in Originals; with a locked layout, edits appear in Processed.
3. Unplug the PC's network mid-burst. The camera should resend; no half files in Inbound FTP or Originals.
4. Tether folder (NX Tether): shoot a burst. Every file imported once, none truncated.
5. Put a deliberately bad file in the queue (e.g. a renamed text file as .jpg). Other photos should keep uploading around it.
6. Check Flickr: photos are Only Me.

## Review notes (not changed)

- The Wi-Fi PTP handoff and USB auto-import only run while their screens are open; moving them into the background service is a bigger change.
- The Android "camera hotspot" gets a new name/password from Android each time it starts (Android limitation).
- Flickr keys and tokens are stored in app settings, which Android backup can include.
- `PhotoMonitorService` is unused legacy code; it's never started.
- How fast a Z8 can actually send over Wi-Fi is set by the camera and Wi-Fi link (well below 120 frames per second of JPEGs). The app now keeps up with whatever arrives and never drops it, but only a real Z8 test can measure throughput.
