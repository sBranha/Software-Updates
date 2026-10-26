# NewsWire v0.7.2 — Detachable Windows

This Windows/macOS desktop development branch follows the image 2 broadcast command-center appearance and the practical controls of image 3.

## What is implemented

- A redesigned dark command-center main map with left navigation.
- **National map**, **Local dashboard**, and **Active warnings** in **separate operating-system windows**.
- Drag/resize any window independently onto another physical monitor using its title bar.
- **Arrange 3 Monitors** opens the three windows and distributes them across available displays. With fewer than three physical monitors, the windows share screens.
- Window positions saved to application user data and restored on the next launch.
- NWS warnings, local warning message, national count, last-check status, and warning selection synchronized from the main map to other windows.
- Selecting a warning in the detached warning list can focus the appropriate active NWS warning on the main map.
- Stream Deck XL gets an **Arrange 3 Desks** action. Existing personal/church Stream Deck profile remains untouched.
- The nationwide NWS request simplifies the Tornado Warning filter and can fall back after HTTP 400, follows NWS pagination, and marks the feed unknown if a complete official answer cannot be obtained.

## How to use

- Main left sidebar: **Local Dashboard ↗** and **Active Warnings ↗** open pop-out windows.
- **Arrange 3 Monitors** opens all windows. They remain normal draggable app windows.
- Menu: **Windows > Local Dashboard**, **Windows > Active Warnings**, **Windows > Arrange on All Monitors**.
- Keyboard: Ctrl/Cmd+1 national map; Ctrl/Cmd+2 local; Ctrl/Cmd+3 warnings; Ctrl/Cmd+Shift+M arrange.
- Drag any window by its title bar onto another monitor. NewsWire remembers positions.
- No paid tiers or premium functionality.

## Limitations and verification

- This version does not yet contain actual real-time reflectivity, storm-relative velocity, automated tornado rotation analysis, or a populated news/forecast feed in the local dashboard. A link to the official NWS radar is provided.
- Stream Deck monitor keys 1/2/3 move the national map window; new Arrange 3 Desks opens other views as separate windows.
- Automated source/unit checks are not a substitute for real three-monitor, real Stream Deck XL, or live NWS outage testing.
- Windows executables and Mac DMGs remain unsigned preview installers. Android Google Play v0.7.0 signed AAB is **not affected**. Do not upload a desktop installer to Google Play.
- This software is supplemental, not a certified public-safety system. Keep Wireless Emergency Alerts and other official warning methods active.
