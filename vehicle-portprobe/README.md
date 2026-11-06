# PortProbe v0.1

Read-only Android USB-C port discovery tester, **not an OBD scanner**.

This first prototype answers a narrow question: What does the phone's Android USB subsystem expose when plugged into a car's existing media/charging USB-C port?

## Features
- One-tap USB connection-state inspection where Android exposes it.
- Enumerates USB host devices, USB interface descriptors and Android USB accessories (no opening data channels).
- Clearly marks vehicle trouble codes and engine ECU data as **not tested**.
- Saves time-stamped JSON reports on the phone, shares the report as an attachment, and displays scan history.
- Vehicle name is editable, so the same APK can be tried on another brand.
- No internet, location, account, OBD commands, USB interface claims, data transmission, clearing codes, or ECU writes.

## Expected limitation
On Android Auto USB ports, the car is commonly the USB host and the phone is the peripheral. Android's host-device enumeration can therefore show zero devices even while the port charges the phone and Android Auto runs. No result from this tester establishes whether manufacturer-specific diagnostics are possible. An OBD-II interface or authorized manufacturer API is typically necessary for trouble codes.

## Build an APK
Use Android SDK platform 35, Java 17, Gradle 8.10.2:
`gradle -p vehicle-portprobe :app:assembleDebug`.
Output: `vehicle-portprobe/app/build/outputs/apk/debug/app-debug.apk`.
A debug APK is signed for direct sideload testing, not Google Play. CI debug signing certificates can change between builds, so installation of a later CI APK may require uninstalling a differently signed prior test version. JSON reports stored in the app are deleted on uninstall; share them before uninstalling.

## Safety
This prototype never opens, claims, or transmits data to any USB endpoint. It only reads Android's enumeration metadata. Test parked, not while driving.
