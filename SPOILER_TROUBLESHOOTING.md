# Spoiler Control Troubleshooting

## Current Status

**Problem**: Port 32960 is not accessible from MazdaControl app, even though JSON mode is implemented.

## Root Cause Analysis

### Fake32960Server Architecture

The decompiled code reveals:

```java
// Fake32960Server runs on port 0x80c0 (32960)
new ServerSocket(0x80c0);  // Line 100

// When data is received, it sends to CarPropertyService
CarPropertyValue property = new CarPropertyValue(0x30000003, data);
mCarProperty.setRawProp(property);  // Line 176
```

**Key findings:**
1. Fake32960Server is **embedded in the original Huron RMU app**
2. It acts as a **bridge**: Network → CarPropertyValue → CarPropertyService
3. The server is **internal** - only accessible from the same app process
4. External apps cannot connect due to Android security model

### Why JSON Mode Doesn't Work

```
Original flow (works):
CarPropertyService → Gson.toJson() → byte[] → Fake32960Server (internal) → Socket

Our flow (doesn't work):
TBoxSpoilerController → JSON → Socket → Fake32960Server ❌ BLOCKED
```

## Solutions

### Option 1: Direct CarPropertyService Access (RECOMMENDED)

Bypass the socket entirely and use **CarPropertyManager API directly**:

```kotlin
// Instead of sending JSON via socket, use CarPropertyManager
val carPropertyManager = context.getSystemService(CarPropertyManager::class.java)
val property = CarPropertyValue(
    propertyId = 0x38,  // Spoiler property
    value = 1,          // OPEN
    status = 0          // VALID
)
carPropertyManager.setProperty(property)
```

**Pros:**
- No socket/port conflicts
- Official Android API
- Works without root

**Cons:**
- Requires CarPropertyManager access (may be restricted)
- Need to find correct property ID format

### Option 2: Inject into Fake32960Server

Use Xposed/Frida to add our command handler to the existing server:

```java
// Xposed hook
findClass("com.mega.rmu.tbox.Fake32960Server", classLoader)
    .getMethod("handleTransparentData", byte[].class)
    .hook { /* Add our JSON handler */ }
```

**Pros:**
- Direct access to working server
- Can intercept all commands

**Cons:**
- Requires Xposed/Frida
- May void warranty
- Security implications

### Option 3: Run Independent Server

Create our own server that mimics Fake32960Server behavior:

```kotlin
class SpoilerServer : ServerSocket(32961) {
    fun handle(json: String) {
        // Parse JSON and send to CarPropertyService via Binder
        val payload = parseJson(json)
        sendToCarProperty(payload)
    }
}
```

**Pros:**
- Full control
- No conflicts

**Cons:**
- Need Binder IPC implementation
- More complex

## Diagnostic Steps

### 1. Check Available Ports

```bash
# Run the port scanner
./scan_ports.sh

# Or manually check
adb shell netstat -tlnp | grep LISTEN
```

### 2. Test Socket Connection

```bash
# Try connecting to port 32960
adb shell nc localhost 32960

# Try our JSON
echo '{"value":1,"valid":true}' | adb shell nc localhost 32960
```

### 3. Monitor CarPropertyService

```bash
# Watch for CarProperty events
adb shell dumpsys car_service | grep -i spoiler

# Or monitor all property changes
adb logcat | grep -i carproperty
```

### 4. Check SELinux

```bash
# Check if SELinux is blocking
adb shell getenforce

# If Enforcing, try temporarily disabling (requires root)
adb shell setenforce 0
```

## Next Steps

1. **Run port scanner**: `./scan_ports.sh`
2. **Check logs**: Look for CarPropertyService activity
3. **Try Option 1**: Implement direct CarPropertyManager access
4. **Consider Option 2**: If Xposed is available

## Files to Investigate

- `analysis/huronRMU/smali/com/mega/rmu/tbox/Fake32960Server.smali` - Original server
- `analysis/huronRMU/smali/mega/car/hardware/property/CarPropertyManager.smali` - Property API
- `app/src/main/java/com/mazda/control/TBoxSpoilerController.kt` - Our controller

## Contact

For questions or updates, create an issue with:
- Port scanner output
- Relevant logcat excerpts
- Which solution option you want to pursue
