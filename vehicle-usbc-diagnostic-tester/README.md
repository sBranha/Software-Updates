# Vehicle USB-C Connection Tester v0.1.0

Android phone-side, read-only USB connectivity probe. It checks the USB power source, available Android USB-state broadcast fields and USB host device enumeration, then saves a local JSON report with an export/share option. It cannot read OBD-II trouble codes via a normal car media/charging USB-C port, and it does not falsely claim to obtain car data. No network or vehicle write permissions.

Install the **debug APK** artifact from GitHub Actions to sideload on Android. No Play Store upload needed. Build: `gradle :app:assembleDebug` from this directory using JDK 17, Android SDK 35 and Gradle 8.10.2.

## Test procedure

1. Park the car. Take one scan with the USB-C cable disconnected.
2. Plug into the existing car USB-C port and scan again (with ignition/accessory power on).
3. Optionally repeat with the engine running **outdoors while parked**.
4. Export both reports. A difference in Android USB state may identify a link, but does not prove the car exposes diagnostic data. Never operate the scanner while driving.

## Privacy

All reports are saved only in app-private local storage unless the user chooses Share or Export. No internet permission is requested. Vendor and product IDs of attached USB devices are collected; serial numbers are not.
