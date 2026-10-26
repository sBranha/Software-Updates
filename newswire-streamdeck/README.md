# NewsWire Stream Deck XL — desktop preview

This is the **first development implementation**, not a finished or hardware-verified plugin.
Supports Stream Deck XL (8 × 4) and other keypad models with Stream Deck 7.1+.
The plugin runs **entirely on the same Windows/Mac computer** as NewsWire. No subscription, telemetry or paid API.

## Installation (after a validated .streamDeckPlugin is packaged)
1. Install and launch the Stream Deck desktop software, version 7.1 or later.
2. Install the NewsWire plugin. In Stream Deck, **create a separate NewsWire profile** rather than replacing an existing profile.
3. Drag NewsWire actions onto that profile. The plugin does not auto-switch or overwrite profiles.
4. Start NewsWire Command Center v0.7.1 desktop app. Keys display **NWS UNKNOWN** if it is not running or its feed is stale.
5. Keep existing alerting channels on. This preview must not be used as a sole life-safety alert.

Suggested XL layout:
Row 1: Warning status, Latest warning, Next warning, Nationwide, Home, Refresh, Resume Auto, Official NWS Radar
Row 2: Satellite, Auto Fly, Voice, Zoom In, Zoom Out, Monitor 1, Monitor 2, Monitor 3
Rows 3 & 4: reserved for future genuine radar products, saved storm views and user shortcuts.
No radar velocity, rotation detection or multi-window synchronized views are implemented. Monitor 1/2/3 **moves the single app window** to the corresponding display when present.

## Technical
The Electron desktop app and plugin exchange short JSON messages in the current user's
`~/.newswire-command-center/streamdeck` directory. Commands are whitelisted and expire
after 30 seconds. The bridge does not listen on a network port or read personal GPS coordinates.
Windows/Mac only; Google Play AAB is not affected. Version 0.7.0 installers **do not** support this plugin.
