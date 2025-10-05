# Miele@home Hubitat Integration

Connect your Miele appliances to Hubitat Elevation for smart home automation and monitoring.

## 🏠 Supported Appliances

- **Washers** - Start, pause, stop programs, monitor status
- **Dryers** - Full program control and monitoring  
- **Dishwashers** - Complete cycle management
- **Ovens** - Temperature control, program management, light control

## ⚡ Quick Start

### 1. Prerequisites

- Hubitat Elevation hub (C-7 or newer recommended)
- Miele@home compatible appliances
- Miele Developer Account ([developer.miele.com](https://developer.miele.com))

### 2. Get Miele API Credentials

1. Register at [developer.miele.com](https://developer.miele.com)
2. Create a new application
3. Set redirect URI to: `https://cloud.hubitat.com/oauth/stateredirect`
4. Note your **Client ID** and **Client Secret**

### 3. Install the Integration

1. **Install the App**:
   - Go to **Apps Code** in Hubitat
   - Click **New App**
   - Paste the `Miele_Connect_Manager.groovy` code
   - Click **Save**

2. **Install Device Drivers**:
   - Go to **Drivers Code** in Hubitat
   - Install each driver you need:
     - `Miele_Washer_Driver.groovy`
     - `Miele_Dryer_Driver.groovy` 
     - `Miele_Dishwasher_Driver.groovy`
     - `Miele_Oven_Driver.groovy`

3. **Add the App**:
   - Go to **Apps** → **Add User App**
   - Select **Miele Connect Manager**
   - Enter your Client ID and Client Secret
   - Choose API version (try "New OpenID Connect API" first)
   - Click **Next**

### 4. Authorize Your Account

1. Click **"Click to authorize with Miele"**
2. Log in with your Miele account
3. Grant permissions
4. You'll be redirected back to Hubitat

### 5. Select Devices

1. Your Miele appliances will be discovered automatically
2. Select which devices to install
3. Click **Done**

## 🎮 Using Your Miele Devices

### Basic Controls

All devices support:
- **Start Program** - Start or resume programs
- **Pause Program** - Pause running programs  
- **Stop Program** - Stop and reset programs
- **Refresh** - Update device status

### Program Management

**Set a Program**:
```
setProgram("Cottons")        // By name
setProgram("1")              // By ID
```

**Available Programs**:
- Use `refreshPrograms` to fetch available programs
- Programs appear in the `availablePrograms` attribute
- Current program shown in `selectedProgram`

### Washer-Specific Features

- **Spin Speed** monitoring
- **Water Consumption** tracking
- **Temperature** monitoring (target and current)
- **Power Estimation** based on operation state

### Oven-Specific Features

- **Temperature Control**: Set target temperature
- **Light Control**: Turn oven light on/off
- **Core Temperature**: Monitor food temperature (if supported)

## 📊 Monitoring & Automation

### Key Attributes

- `operationState` - Current operation (e.g., "Running", "Finished")
- `remainingTime` - Minutes remaining in program
- `programPhase` - Current phase (e.g., "Washing", "Rinsing")
- `doorState` - Door status
- `selectedProgram` - Currently selected program name

### Rule Machine Examples

**Notify when wash is done**:
```
IF operationState changes to "Finished"
THEN Send notification "Laundry is ready!"
```

**Start dishwasher at off-peak hours**:
```
IF Time is 11:00 PM
AND operationState is "Waiting to start"  
THEN Run startProgram
```

## 🔧 Troubleshooting

### Common Issues

**"Client not found" Error**:
1. Verify Client ID is correct (no extra spaces)
2. Try switching API versions in settings
3. Ensure your Miele developer account is approved
4. Check redirect URI: `https://cloud.hubitat.com/oauth/stateredirect`

**Commands Don't Work**:
1. Check that **Mobile Start** is enabled on your appliance
2. Ensure appliance is in correct state (e.g., "Waiting to start")
3. Some newer appliances have limited API support until Fall 2025

**Programs List Empty**:
- Many newer Miele appliances don't support program listing
- You can still use program IDs: "1", "2", "3", etc.
- Try common program names: "Cottons", "Delicates", "Quick Wash"

**Resume Not Working**:
- Use **"Start Program"** to resume after pause
- Miele washers use the same command for start and resume

### Manual OAuth Completion

If automatic OAuth fails:
1. Copy the authorization code from the redirect URL
2. Enter it in the "Manual OAuth Completion" section
3. Click "Complete OAuth"

### Getting Help

1. Check Hubitat logs for detailed error messages
2. Use "Test API Connection" button for diagnostics
3. Verify your appliance model supports the Miele API

## ⚠️ API Compatibility Notice

**Next-generation Miele appliances** (washers, dryers, vacuum cleaners) have **limited API support until Fall 2025**. 

- ✅ **Monitoring works** - Status, time remaining, etc.
- ⚠️ **Control commands may be limited** - Start/stop/pause might not work
- 🔄 **Full support coming** - Miele is updating their API in Fall 2025

## 🔄 Updates & Maintenance

- **Token Refresh**: Automatic (24-hour tokens)
- **Device Discovery**: Automatic on startup
- **Polling**: Adaptive (1 min when active, 5 min when idle)
- **Error Recovery**: Automatic retry with exponential backoff

## 📝 Version History

### v1.0.0 (Initial Release)
- OAuth 2.0 authentication with both legacy and new APIs
- Support for Washers, Dryers, Dishwashers, and Ovens
- Program management and control
- Real-time status monitoring
- Power estimation and energy tracking
- Comprehensive error handling and recovery

## 🤝 Contributing

Found a bug or want to contribute? Visit our [GitHub repository](https://github.com/your-repo/miele-hubitat) to:
- Report issues
- Submit feature requests  
- Contribute code improvements

## 📄 License

Licensed under the Apache License, Version 2.0. See LICENSE file for details.

---

**Enjoy your connected Miele appliances!** 🎉