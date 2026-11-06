# Vehicle Port Tester v0.5.0

Read-only USB discovery and an optional, user-confirmed accessory open/close test for a parked vehicle. Preserves the separate saved deep discovery report from v0.4.1.

## What is new

- **5 TEST USB OPEN / CLOSE** asks for explicit confirmation before attempting to open the Android USB accessory and immediately close its file descriptor.
- Checks for accessory enumeration and permission first; records open, close, outcome and exception type.
- Does not read or write USB stream data, send vehicle commands, or query vehicle control or OBD-II interfaces.
- Stores the connection test independently from scan status broadcasts and normal deep scan records; **EXPORT SAVED USB CONNECTION TEST** exports its JSON.
- Adds modern adaptive Android launcher icon resources using the Explorer image, with versionCode 6 and versionName 0.5.0.
- Maintains the same package ID `com.newswire.vehicleporttester.v2`.

## Safety

**Run only while parked.** Opening an accessory can interrupt Android Auto. A successful open only confirms that the Android USB accessory file descriptor was opened; it does not establish Ford SYNC firmware access, in-car application inventory, diagnostic access or vehicle-computer communications. No automatic takeovers or background open tests are performed.

## Installation/signing

The GitHub Actions output is a debug-signed development APK for compilation testing, **not** a guaranteed in-place update. An actual update over the installed v0.4.1 APK must have the **same Android app signing private key and package ID**, and a higher versionCode. Do not uninstall v0.4.1 merely to install the CI artifact. Sign the v0.5.0 APK with the protected persistent v0.4.1 key before providing the upgrade to the user; do not upload that key to the repository.
