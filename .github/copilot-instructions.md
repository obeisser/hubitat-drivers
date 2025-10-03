# Copilot Instructions for Hubitat WLED Driver Codebase

## Project Overview
- This repository contains community drivers for Hubitat Elevation, with a focus on the advanced WLED Universal Driver (`WLED/WLED_Universal.groovy`).
- The WLED driver is designed for robust, asynchronous control of WLED devices, supporting name-based effect, palette, and playlist selection, error recovery, and health monitoring.

## Key Architectural Patterns
- **Single-file Driver:** All logic for the WLED driver is in `WLED/WLED_Universal.groovy`. No external Groovy modules are imported.
- **State Management:** Uses the `state` object for runtime data (effects, palettes, playlists, connection info).
- **Hubitat API:** Relies on Hubitat's device API (`sendEvent`, `runIn`, `asynchttpGet`, `asynchttpPost`).
- **Command/Attribute Model:** Exposes commands and attributes via the `metadata` block for Hubitat UI and automation integration.
- **Error Handling:** Implements auto-retry logic for network errors, with exponential backoff and connection state tracking.
- **Name-based Control:** Effect, palette, and playlist selection can be done by name (with smart matching) or by ID.

## Developer Workflows
- **No Build Step:** Drivers are plain Groovy files, imported directly into Hubitat via copy-paste or URL. No compilation or packaging required.
- **Testing:** Manual testing is performed in the Hubitat UI by invoking commands and observing state variables. No automated test suite is present.
- **Debugging:** Enable debug logging via the `logEnable` preference. Use Hubitat's log viewer for troubleshooting.
- **Initialization:** After installation, run `forceRefresh()` to load effects, palettes, and playlists from the WLED device.

## Project-Specific Conventions
- **Effect/Palette/Playlist Lists:** Use `listEffects()`, `listPalettes()`, and `listPlaylists()` to update available options. These are stored in state and exposed as attributes.
- **Segment Handling:** The driver supports multi-segment WLED devices. Segment ID is set via preferences and used in all commands.
- **Reverse Effect:** Control effect direction with `reverseOn()`, `reverseOff()`, and `toggleEffectDirection()`.
- **Error Recovery:** Network errors trigger retries up to 3 times, then log permanent failure.
- **Polling:** Device state is refreshed on a schedule, configurable via preferences.

## Integration Points
- **WLED API:** Communicates with WLED via `/json/state`, `/json`, `/json/info`, `/json/playlists` endpoints.
- **Hubitat Platform:** All device interaction is through Hubitat's Groovy API; no external dependencies.

## Example Usage Patterns
- Set effect by name: `setEffectByName("Rainbow", 150, 200, "Rainbow")`
- List available effects: `listEffects()`
- Start playlist by name: `setPlaylistByName("Evening Routine")`
- Reverse effect direction: `reverseOn()` / `reverseOff()`
- Check connection: `testConnection()`

## Key Files & Directories
- `WLED/WLED_Universal.groovy`: Main driver source code
- `WLED/readme.md`: Feature overview, usage examples, troubleshooting
- `readme.md`: Repository-level summary

---

**For AI agents:**
- Focus on the single-file driver architecture and Hubitat-specific patterns.
- Use the state object and attributes for all runtime and UI data.
- All device communication is asynchronous and must handle network errors gracefully.
- No build, test, or CI/CD steps are present; all workflows are manual and UI-driven.

If any conventions or workflows are unclear, ask for clarification or examples from the user.
