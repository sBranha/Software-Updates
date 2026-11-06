# Vehicle Port Tester v0.3.0

A read-only Android USB connection diagnostic tester for comparing unplugged, USB-C and regular USB-A vehicle-port states.

## What's new
- Based on v0.2.0; preserves three labeled scans, saved comparisons and JSON export.
- Displays real scan progress checkpoints and a completion timestamp for every scan.
- Uses a high-contrast red 2021 Ford Explorer launcher icon.
- Keeps the **v0.2 app ID** `com.newswire.vehicleporttester.v2` so it does not create a third app identity.
- Must have a matching signing certificate to update an installed APK. The previous debug builds used ephemeral GitHub runner keys, so this build will be re-signed using a stable local signing key. v0.2 must be uninstalled once before installing the new signer; that erases its app-private saved scans, so export any reports first.
- Future APKs must use the **same stored private keystore**, not a fresh temporary key.

The scan is read-only. A USB connection or Android Auto pairing does **not** imply engine diagnostics, and this app does not read OBD-II codes.

## Install/test
After backup of previous v0.2 reports, uninstall v0.2 if Android reports signature mismatch and install v0.3 directly. With the vehicle parked and ignition on, capture unplugged, USB-C, and USB-A states and export the combined JSON.
