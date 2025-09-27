# Optimized WLED Driver for Hubitat Elevation

This repository contains a heavily optimized and feature-complete Hubitat Elevation driver for controlling WLED devices. It builds upon the foundational work of the original driver by Bryan Li and has been re-architected for stability, performance, and full dashboard compatibility on the C-8 platform.

## Key Features

* **⚡️ Fully Asynchronous:** All network communication is non-blocking, ensuring your Hubitat hub remains fast and responsive.
* **✅ Robust & Resilient:** A smart initialization sequence prevents startup errors. The driver can self-heal from temporary network blips or desynchronization.
* **🚀 Highly Efficient:** Reduces network traffic by intelligently fetching data and only updates Hubitat when values have actually changed, minimizing hub load.
* **🎨 Full Dashboard Compatibility:** Works perfectly with the "Color Temperature Light" tile by correctly reporting the `colorTemperature` attribute, even when WLED only provides RGB data.
* **💡 Complete Control:** Supports on/off, level, color, color temperature, presets, and all WLED effects and palettes.

---
## Installation

1.  In your Hubitat hub, navigate to the **Drivers Code** section.
2.  Click the **+New Driver** button in the top right.
3.  Click the **Import** button.
4.  Paste the following URL into the text box:
    ```
    [https://raw.githubusercontent.com/obeisser/hubitat-drivers/main/WLED/WLED-Optimized.groovy](https://raw.githubusercontent.com/obeisser/hubitat-drivers/main/WLED/WLED-Optimized.groovy)
    ```
5.  Click **Import**, then **Save**.
6.  Navigate to your WLED device in your **Devices** list.
7.  In the "Device Information" section, change the **Type** to **WLED Optimized** (it will be at the bottom under "User Drivers").
8.  Click **Save Device**.
9.  Click the **forceRefresh** command button to complete the initialization.

---
## Configuration

* **WLED URI:** The IP address of your WLED controller (e.g., `http://192.168.1.123`).
* **LED Segment ID:** The WLED segment you want to control (usually `0`).
* **Polling Refresh Interval:** How often Hubitat should check the WLED device for status changes.

---
## Credits

* **Original Driver:** Bryan Li (bryan@joyful.house)
* **Optimization & Refactoring:** Oliver Beisser