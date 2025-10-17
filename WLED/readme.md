# Enhanced WLED Driver for Hubitat Elevation v1.3.4

A comprehensive, Hubitat Elevation community driver for WLED devices with advanced features, name-based control, and robust error handling. Built upon the original work by Bryan Li and extensively enhanced for modern WLED installations.

## 🚀 Key Features

### **Smart Control**
* **🎨 Unified Control:** Control effects, palettes, presets, and playlists by name OR ID number.
* **🔄 Reverse Effect Switch:** Easy automation control with dedicated on/off commands.
* **📋 Advanced Preset Management:** Save, update, and delete presets with detailed parameter control.
* **🎯 Smart Matching:** Intelligent exact and partial name matching for all commands.
* **🌙 Nightlight Control:** Full nightlight implementation with parameter control.

### **Reliability**
* **⚡️ Fully Asynchronous:** Non-blocking network calls keep your hub responsive.
* **🔄 Auto-Retry Logic:** Automatic retry with exponential backoff for failed requests.
* **💓 Health Monitoring:** Connection state tracking with automatic recovery.
* **🛡️ Error Resilience:** Self-healing from network issues and API errors.

### **Advanced Features**
* **📊 Enhanced State Variables:** Shows both names and ID numbers for all attributes.
* **🔍 Discovery Commands:** List all available effects, palettes, and playlists.
* **📱 Dashboard Compatible:** Perfect integration with Hubitat dashboards.
* **🎛️ Complete WLED API:** Supports segments, presets, effects, palettes, and playlists.

---

## 📋 Available Commands

### **Basic Control**
```groovy
on()                    // Turn on
off()                   // Turn off
setLevel(50)           // Set brightness to 50%
setColor([hue: 120, saturation: 100, level: 75])
setColorTemperature(3000)
```

### **Unified Control (Name or ID)**
```groovy
// All commands accept either a name or an ID number
setEffect("Rainbow", 150, 200, "Party")  // Set effect by name, with speed, intensity, and palette
setEffect(9, 128, 128, 11)              // Same command works with IDs

setPalette("Ocean")                     // Set palette by name
setPalette(22)                          // Set palette by ID

setPreset("My Favorite")                // Set preset by name
setPreset(5)                            // Set preset by ID

setPlaylist("Evening Routine")          // Set playlist by name
setPlaylist(3)                          // Set playlist by ID
```

### **Advanced Preset Management**
```groovy
// Save current state to a new preset with an auto-assigned ID
saveCurrentAsPreset("My New Look")

// Save current state, overwriting preset ID 15
saveCurrentAsPreset("My Updated Look", 15)

// Create a complex new preset (or update existing) with custom parameters
// Creates preset with ID 11, name "Red Alert", brightness 200, Strobe effect, and red color
savePreset("Red Alert", 11, 200, "Strobe", null, 255, 255, "FF0000")

// Delete preset by ID
deletePreset(12)

// Advance to the next preset in the running playlist
nextPresetInPlaylist()
```

### **Nightlight Control**
```groovy
setNightlight(10, "Fade", 50)  // Turn on nightlight for 10 mins, fade mode, 50/255 brightness
nightlightOff()                // Turn off nightlight
```

### **Reverse Effect Control**
```groovy
reverseOn()              // Turn reverse effect ON
reverseOff()             // Turn reverse effect OFF
toggleEffectDirection()  // Toggle current direction
```

### **Discovery & Management**
```groovy
listEffects()           // Show all available effects with IDs
listPalettes()          // Show all available palettes with IDs
listPresets()           // Show all available presets with IDs
listPlaylists()         // Show all available playlists with IDs
getDeviceInfo()         // Get WLED firmware version and device info
testConnection()        // Test connection health
forceRefresh()          // Force a full refresh of all data from the device
```

---

## 📊 State Variables

The driver provides comprehensive state information for use in dashboards and automations. Key attributes include:
- `switch`, `level`, `colorName`, `connectionState`
- `effectName`, `effectId`, `paletteName`, `paletteId`
- `presetName`, `presetValue`, `playlistName`, `playlistId`, `playlistState`
- `nightlightActive`, `nightlightDuration`, `nightlightRemaining`
- `availableEffects`, `availablePalettes`, `availablePresets`, `availablePlaylists`
- `firmwareVersion`

---

## ⚙️ Configuration Options

- **WLED URI:** Device IP address (e.g., `http://192.168.1.123`).
- **LED Segment ID:** Target segment (usually `0`).
- **Polling Refresh Interval:** Status check frequency.
- **Enable Auto-Retry:** Automatic retry for failed commands.
- **Connection Monitoring:** Health monitoring with auto-recovery.
- **Show Deprecated Commands:** Show legacy `ByName` commands in the UI (they remain available for backward compatibility in automations regardless).

---

## 🛠️ Installation

### Method 1: Hubitat Package Manager (HPM) - Recommended
1.  In HPM, select **Install** > **Search by Keywords**.
2.  Search for **"WLED Universal"** and select it.
3.  Follow the on-screen instructions to complete the installation. HPM will handle updates automatically.

### Method 2: Manual Installation
1.  Navigate to **Drivers Code** in your Hubitat hub.
2.  Click **+New Driver** > **Import**.
3.  Paste the driver's raw GitHub URL and click **Import**, then **Save**.

### Device Setup (for new devices)
1.  Go to **Devices** and select your WLED device.
2.  In the **Device Information** section, change the **Type** to **WLED Universal**.
3.  Configure the **WLED URI** (e.g., `http://192.168.1.123`) and any other preferences.
4.  Click **Save Device**.
5.  Run the **forceRefresh** command to initialize the driver and fetch data from your WLED device.

---

## 🔄 Version History

### **v1.3.5**
**Bug Fixes & Enhancements:**
- **Intuitive Set Commands:** Set commands (setEffect, setPalette, setPreset, setPlaylist) now automatically turn on device
- **Fixed Playlist Control:** Playlists now properly turn on device and can be stopped with off() command
- **Enhanced Preset Management:** Fixed data type handling errors and added tertiary color support

### **v1.3.4**
**Performance & Stability:**
- **Tertiary Color Support:** Added tertiary color support for saving presets with complete RGB control
- **State Variables Cleanup:** Resolved display issues where complex JSON data cluttered the device page
- **Improved Initialization:** Optimized timing and reliability of data loading during startup
- **Performance Enhancements:** Implemented batch attribute updates for better synchronization

### **v1.3.3**
**UX Enhancements:**
- **Unified Commands:** `setEffect`, `setPalette`, `setPreset`, and `setPlaylist` now accept both names (e.g., "Rainbow") and IDs (e.g., 9). Legacy `ByName` commands are now deprecated. All effect/palette/preset/playlist commands now intelligently handle mixed parameter types
- **Advanced Preset Control:**
    - `savePreset`: Create or update presets with custom parameters (brightness, effect, colors). Auto-assigns an ID if not provided.
    - `saveCurrentAsPreset`: Save the current WLED state to a new or existing preset.
    - `deletePreset`: Delete presets by ID.
    - `nextPresetInPlaylist`: Advance to the next preset in a running playlist.
- **Code Refinements:** Consolidated network settings and improved retry logic for better reliability.

### **v1.3.2**
**Improvements & Fixes:**
- **Improved Preset Handling:** Reworked how presets are fetched, parsed, and managed.
- **API Correction:** Fixed endpoints and data parsing for presets to align with modern WLED firmware.
- **Data Separation:** Playlists are now correctly separated from the preset list.

### **v1.3.1**
**Fixed Issues:**
- The `setNightlight` command now ensures the master power is turned on, providing a more intuitive user experience.

### **v1.3.0**
**Added Features:**
- Added complete nightlight implementation with on/off and parameter control.
- Added nightlight status attributes for monitoring.

*(For full history, see driver file)*

---

## 🤝 Credits & Support

- **Original Driver:** Bryan Li (bryan@joyful.house)
- **Rewrite & Enhancements:** Oliver Beisser

This driver is tested with WLED v0.13+ and is optimized for the Hubitat C-8 platform, while remaining compatible with older hubs.

---

## 🔧 Troubleshooting

### **Connection Issues**
- Check WLED URI format: `http://192.168.1.123` (no trailing slash)
- Verify network connectivity between Hubitat and WLED device
- Use `testConnection()` command to diagnose issues
- Enable debug logging for detailed error information
- Check connection state attribute for detailed status:
  - `connected` - Normal operation
  - `timeout` - Network timeout issues
  - `unreachable` - Device not responding
  - `protocol_error` - WLED API communication issues

### **Effect/Playlist Not Found**
- Use `listEffects()`, `listPalettes()`, or `listPlaylists()` to see available options
- Try partial name matching: "fire" instead of "Fire 2012"
- Check spelling and capitalization
- Refresh device with `forceRefresh()` to reload lists
- Verify WLED firmware version compatibility (v0.13+ recommended)

### **Backward Compatibility**
- **Legacy Rule Machine Rules**: Existing rules using integer IDs (e.g., `setEffect(15, 0, 100, 0)`) continue to work seamlessly
- **Mixed Usage**: You can mix IDs and names in the same command: `setEffect(15, 128, 128, "Rainbow")`
- **String IDs**: Both `setEffect(15, ...)` and `setEffect("15", ...)` are supported
- **Migration**: No need to update existing automations - they remain fully compatible
- **Method Signatures**: The driver automatically detects whether you're passing numbers or strings

### **Performance Issues**
- Reduce polling frequency if experiencing hub slowdown
- Enable auto-retry for better reliability with exponential backoff
- Check connection state regularly
- Monitor logs for network errors
- Consider disabling connection monitoring on stable networks
- Use batch operations when possible (multiple commands in sequence)

### **Memory and Stability**
- Avoid excessive polling on devices with many effects/palettes
- Monitor hub memory usage if using multiple WLED devices
- Clear device logs periodically to prevent memory buildup
- Restart driver if experiencing persistent connection issues

---

## 📚 Additional Resources

- [WLED Official Documentation](https://kno.wled.ge/)
- [WLED API Reference](https://kno.wled.ge/interfaces/json-api/)
- [Hubitat Documentation](https://docs.hubitat.com/)
- [Community Forum Support](https://community.hubitat.com/)

---

*This driver represents a comprehensive solution for WLED control in Hubitat environments with user-friendly features for both beginners and advanced users.*