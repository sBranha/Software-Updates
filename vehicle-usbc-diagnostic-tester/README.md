# Vehicle Port Tester v0.4.0

Android USB-C capability investigator for a parked 2021 Ford Explorer, developed on the working v0.3 application. This does **not** claim general access to Ford SYNC, Android Auto traffic, or vehicle electronic control units.

## What v0.4 adds
- A fourth **Deep USB-C Discovery** capture, while preserving unplugged, USB-C and USB-A comparisons.
- Android USB device, interface and endpoint descriptors, plus limited raw descriptor reads for host-side devices **only when Android has granted permission**.
- Automotive accessory metadata, permissions, and Android's confirmation or denial of user-initiated USB access requests.
- Phone-side apps advertising Android for Cars or Android Auto media service declarations, labeled as visible candidates. This is not the SYNC-installed app inventory or proof an app is running on the car display.
- Combined export includes the deep report; progress and local report storage remain.
- The same red Explorer icon and application ID as v0.3: `com.newswire.vehicleporttester.v2`, versionCode 4.

## Safety and technical limits
No arbitrary USB payloads, undocumented Ford commands, vehicle writes, OBD-II queries, or connection takeovers. Standard Android public APIs cannot enumerate software installed on the Ford head unit. Android Auto's accessory descriptor is not a general diagnostic endpoint. If Android permission is available, the app requests it using the system permission prompt; the deep scan does not open an in-use accessory channel.

## Installation/signing requirement
Android allows an update in place only when **the package ID and signing certificate match**. This project retains the package ID. GitHub Actions produces a build using the runner's debug key, which is not a stable signing identity. Do **not** install that build expecting it to update a v0.3 APK: it must be re-signed using the **same private key that signed the installed v0.3 APK**. If that key is unavailable, an in-place update preserving app-private data is not possible. Never commit a private keystore to the public repository.

## Usage
Park the Explorer, ignition in accessory/on, connect the USB-C data cable, tap **REQUEST USB ACCESS PERMISSION** if needed, respond to Android's prompt, then **DEEP USB-C DISCOVERY**. You can compare unplugged, USB-C and USB-A scans and export a JSON report locally for follow-up analysis.
