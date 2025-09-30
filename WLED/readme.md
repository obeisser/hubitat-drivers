# Enhanced WLED Driver for Hubitat Elevation v1.2

A comprehensive, Hubitat Elevation community driver for WLED devices with advanced features, name-based control, and robust error handling. Built upon the original work by Bryan Li and extensively enhanced for modern WLED installations.

## 🚀 Key Features

### **Smart Control**
* **🎨 Name-Based Selection:** Control effects, palettes, and playlists by name instead of numbers
* **🔄 Reverse Effect Switch:** Easy automation control with dedicated on/off commands
* **📋 Playlist Support:** Full playlist management with name-based selection
* **🎯 Smart Matching:** Intelligent exact and partial name matching with fallbacks

### **Enterprise Reliability**
* **⚡️ Fully Asynchronous:** Non-blocking network calls keep your hub responsive
* **🔄 Auto-Retry Logic:** Automatic retry with exponential backoff for failed requests
* **💓 Health Monitoring:** Connection state tracking with automatic recovery
* **🛡️ Error Resilience:** Self-healing from network issues and API errors

### **Advanced Features**
* **📊 Enhanced State Variables:** Shows both names and ID numbers for all attributes
* **🔍 Discovery Commands:** List all available effects, palettes, and playlists
* **📱 Dashboard Compatible:** Perfect integration with Hubitat dashboards
* **🎛️ Complete WLED API:** Supports segments, presets, effects, palettes, and playlists

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

### **Name-Based Control (New!)**
```groovy
setEffectByName("Rainbow", 150, 200, "Rainbow")  // Effect, speed, intensity, palette
setEffectByName("Fire 2012", 255, 255)          // Just effect and parameters
setPaletteByName("Ocean")                        // Change palette by name
setPlaylistByName("Evening Routine")             // Start playlist by name
```

### **Traditional ID Control**
```groovy
setEffect(9, 128, 128, 11)    // Effect ID, speed, intensity, palette ID
setPreset(5)                  // Load preset 5
setPlaylist(3)                // Start playlist 3
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
listPlaylists()         // Show all available playlists with IDs
getDeviceInfo()         // Get WLED firmware version and device info
testConnection()        // Test connection health
```

---

## 📊 State Variables

The driver provides comprehensive state information:

### **Current Status**
- `switch` - On/off state
- `level` - Brightness percentage
- `colorName` - Human-readable color name
- `connectionState` - Connection health (connected/error/testing)
- `lastUpdate` - Last successful update timestamp

### **Effect Information**
- `effectName` - Current effect name (e.g., "Rainbow")
- `effectId` - Current effect ID number (e.g., 9)
- `effectDirection` - Effect direction (forward/reverse)
- `reverse` - Reverse switch state (on/off)

### **Palette Information**
- `paletteName` - Current palette name (e.g., "Rainbow")
- `paletteId` - Current palette ID number (e.g., 11)

### **Playlist Information**
- `playlistName` - Current playlist name (e.g., "Evening Routine")
- `playlistId` - Current playlist ID number (e.g., 5)
- `playlistState` - Playlist status (running/stopped/none)

### **Available Options**
- `availableEffects` - Complete list: "0: Solid, 1: Blink, 2: Breathe, 9: Rainbow..."
- `availablePalettes` - Complete list: "0: Default, 1: Random, 2: Rainbow..."
- `availablePlaylists` - Complete list: "1: Morning, 3: Evening, 5: Party..."

### **Device Information**
- `firmwareVersion` - WLED firmware version
- `presetValue` - Current preset number

---

## ⚙️ Configuration Options

### **Required Settings**
- **WLED URI:** Device IP address (e.g., `http://192.168.1.123`)
- **LED Segment ID:** Target segment (usually `0`)

### **Performance Settings**
- **Polling Refresh Interval:** Status check frequency (30s to 1hr, or disabled)
- **Default Transition Time:** Animation duration for changes

### **Advanced Options**
- **Enable Auto-Retry:** Automatic retry for failed commands
- **Connection Monitoring:** Health monitoring with auto-recovery
- **Power Off Main Controller:** Turn off entire WLED device with segment

---

## 🛠️ Installation

### **Method 1: Direct Import (Recommended)**
1. Navigate to **Drivers Code** in your Hubitat hub
2. Click **+New Driver** → **Import**
3. Paste the raw GitHub URL for the driver
4. Click **Import** → **Save**

### **Method 2: Manual Installation**
1. Copy the entire driver code
2. Navigate to **Drivers Code** → **+New Driver**
3. Paste the code and click **Save**

### **Device Setup**
1. Go to your WLED device in **Devices**
2. Change **Type** to **WLED Optimized**
3. Configure the WLED URI and segment ID
4. Click **Save Device**
5. Run **forceRefresh** to initialize

---

## 💡 Usage Examples

### **Rule Machine Integration**
```groovy
// Morning routine
device.setPlaylistByName("Morning Lights")

// Party mode with specific effect
device.setEffectByName("Rainbow", 200, 255, "Party")

// Conditional control
if (device.currentValue("effectName") == "Solid") {
    device.setEffectByName("Fire")
}

// Reverse control for automations
if (device.currentValue("reverse") == "off") {
    device.reverseOn()
}
```

### **WebCoRE Integration**
```groovy
// Smart matching - "fire" matches "Fire 2012"
device.setEffectByName("fire", 255, 255)

// Check connection before commands
if (device.currentValue("connectionState") == "connected") {
    device.setPlaylistByName("Evening")
}
```

---

## 🔧 Troubleshooting

### **Connection Issues**
- Check WLED URI format: `http://192.168.1.123` (no trailing slash)
- Verify network connectivity between Hubitat and WLED device
- Use `testConnection()` command to diagnose issues
- Enable debug logging for detailed error information

### **Effect/Playlist Not Found**
- Use `listEffects()`, `listPalettes()`, or `listPlaylists()` to see available options
- Try partial name matching: "fire" instead of "Fire 2012"
- Check spelling and capitalization
- Refresh device with `forceRefresh()` to reload lists

### **Performance Issues**
- Reduce polling frequency if experiencing hub slowdown
- Enable auto-retry for better reliability
- Check connection state regularly
- Monitor logs for network errors

---

## 🔄 Version History

### **v1.2.1 (Latest)**

**Added Features:**
- Added command descriptions for better user interface clarity

**Fixed Issues:**
- Fixed alarm effects to use actual available WLED effects (Chase Flash, Strobe, Strobe Mega)

### **v1.2**

**Added Features:**
- Added effect selection by name with smart matching (exact and partial)
- Added palette selection by name with intelligent fallback
- Added comprehensive playlist support with name-based control
- Added reverse effect switch capability for easier automation control
- Added discovery commands: `listEffects()`, `listPalettes()`, `listPlaylists()`
- Added device info tracking and firmware version reporting
- Added support for additional WLED API endpoints (`/json/info`, `/json/playlists`)
- Added new attributes: `effectId`, `paletteId`, `playlistId`, `playlistName`, `playlistState`, `reverse`
- Added new commands: `reverseOn()`, `reverseOff()`, `getDeviceInfo()`, `testConnection()`

**Improved Features:**
- Improved state variables to show effect/palette/playlist IDs alongside names
- Improved retry logic for failed network requests with exponential backoff
- Improved error recovery and connection state management with health monitoring
- Improved segment validation and error handling with detailed logging
- Improved code organization with constants for better maintainability
- Improved documentation and modular architecture

**Fixed Issues:**
- Fixed null pointer exceptions in state synchronization methods
- Fixed boolean handling in switch and level calculations
- Fixed @Field constant accessibility issues in Hubitat environment
- Fixed device info parsing from WLED API response structure
- Fixed playlist information handling with proper null safety

### **v1.1**
- Added reverse effect direction control
- Improved state synchronization
- Enhanced error handling

### **v1.0**
- Initial optimized release
- Asynchronous operations
- Dashboard compatibility
- Basic WLED API support

---

## 🤝 Credits & Support

* **Original Driver:** Bryan Li (bryan@joyful.house)
* **Optimization & Refactoring:** Oliver Beisser
* **Enhanced Features:** Kiro AI Assistant

### **WLED Compatibility**
- Tested with WLED v0.13+ 
- Supports all standard WLED features
- Compatible with ESP8266 and ESP32 devices
- Works with single and multi-segment configurations

### **Hubitat Compatibility**
- Optimized for Hubitat C-8 platform
- Compatible with C-7 and earlier models
- Full dashboard tile support
- Rule Machine and WebCoRE integration

---

## 📚 Additional Resources

- [WLED Official Documentation](https://kno.wled.ge/)
- [WLED API Reference](https://kno.wled.ge/interfaces/json-api/)
- [Hubitat Documentation](https://docs.hubitat.com/)
- [Community Forum Support](https://community.hubitat.com/)

---

*This driver represents a comprehensive solution for WLED control in Hubitat environments with user-friendly features for both beginners and advanced users.*
