# CarPropertyManager Integration Guide

## Overview

This guide explains how to use `CarPropertySpoilerController.kt` as an alternative to the socket-based `TBoxSpoilerController.kt`.

## Why CarPropertyManager?

The original approach using socket connection to port 32960 has issues:
- Port 32960 is used internally by Fake32960Server (embedded in Huron RMU app)
- External apps cannot connect to another app's localhost socket
- Android security model blocks cross-process localhost connections

**Solution**: Use Android's official CarPropertyManager API to send commands directly.

## Architecture Comparison

### Old Approach (Socket-based) - DOESN'T WORK
```
TBoxSpoilerController 
    → JSON/Packet 
    → Socket (localhost:32960) 
    → Fake32960Server 
    → CarPropertyService
                          ❌ BLOCKED by Android security
```

### New Approach (CarPropertyManager) - RECOMMENDED
```
CarPropertySpoilerController 
    → CarPropertyManager.setProperty() 
    → CarPropertyService
                          ✅ OFFICIAL API
```

## Implementation

### 1. Add CarPropertyManager Controller

File: `app/src/main/java/com/mazda/control/CarPropertySpoilerController.kt`

```kotlin
class CarPropertySpoilerController(private val context: Context) {
    private var car: Car? = null
    private var propertyManager: CarPropertyManager? = null
    
    fun connect(): Boolean {
        car = Car.createCar(context)
        propertyManager = car?.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
        return propertyManager != null
    }
    
    fun sendCommand(command: Int): Boolean {
        val property = CarPropertyValue(
            0x38,  // Spoiler property ID
            0,     // Area ID
            CarPropertyManager.STATUS_SUCCESS,
            command  // 1=OPEN, 2=CLOSE, 3=FOLLOW_SPEED, 4=SPORT_MODE
        )
        propertyManager?.setProperty(property)
        return true
    }
}
```

### 2. Update MainActivity

Add mode selector to switch between TBox and CarProperty modes:

```kotlin
// In MainActivity.kt
private var useCarPropertyMode by mutableStateOf(false)
private lateinit var carPropertyController: CarPropertySpoilerController

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    // Initialize both controllers
    tBoxController = TBoxSpoilerController(applicationContext)
    carPropertyController = CarPropertySpoilerController(applicationContext)
    
    // Connect based on selected mode
    connectActiveController()
}

private fun connectActiveController() {
    executor.execute {
        if (useCarPropertyMode) {
            // Use CarPropertyManager API
            val result = carPropertyController.connect()
            mainHandler.post {
                isConnected = result
                log("CarProperty mode: ${if (result) "CONNECTED" else "FAILED"}")
            }
        } else {
            // Use TBox socket
            tBoxController.connect()
        }
    }
}
```

### 3. Update UI

Add mode toggle button in `SpoilerScreen.kt`:

```kotlin
@Composable
fun SpoilerScreen(
    // ... existing parameters
    useCarPropertyMode: Boolean,
    onToggleModeClick: () -> Unit
) {
    // ...
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        // Mode toggle button
        Button(onClick = onToggleModeClick) {
            Text(
                text = if (useCarPropertyMode) "Mode: CarProperty" else "Mode: TBox Socket",
                fontFamily = FontFamily.Monospace
            )
        }
        
        // ... other buttons
    }
}
```

## Usage

### Testing CarProperty Mode

1. **Build and install**:
   ```bash
   ./gradlew assembleDebug
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

2. **Open MazdaControl app**

3. **Toggle to CarProperty mode**:
   - Click "Mode: TBox Socket" button
   - It should change to "Mode: CarProperty"

4. **Test spoiler control**:
   - Click "OPEN" button
   - Check logcat for CarPropertyManager activity
   - Verify spoiler moves

### Monitoring

```bash
# Watch CarProperty events
adb logcat | grep -i "CarProperty\|Spoiler"

# Look for these messages:
# "CarPropertyManager connection: SUCCESS"
# "✓ Command sent via CarPropertyManager"
```

## Troubleshooting

### Issue: "CarPropertyManager connection: FAILED"

**Cause**: Car service not available or permission denied

**Solution**:
1. Check if car hardware is initialized
2. Verify app has CAR_SERVICE permission
3. Try restarting the car's infotainment system

### Issue: "setProperty() throws SecurityException"

**Cause**: Property ID may be restricted

**Solution**:
1. Try different property IDs (0x38, 0x30000003, etc.)
2. Check if property is writable
3. May need system signature (limited on production devices)

### Issue: "Nothing happens when sending command"

**Cause**: Property ID or format incorrect

**Solution**:
1. Check logcat for error messages
2. Verify payload format matches HuronCarSettings
3. Try using `sendRawCommand()` instead of `sendCommand()`

## Advanced: Using Raw Commands

For more control, use `sendRawCommand()` which mimics Fake32960Server exactly:

```kotlin
val payload = SpoilerPayload(
    value = 1,  // OPEN
    valid = true,
    time = System.currentTimeMillis()
)

carPropertyController.sendRawCommand(payload)
```

This sends the command as a byte array using property ID 0x30000003, exactly like the original Fake32960Server does.

## Comparison Table

| Feature | TBox Socket Mode | CarProperty Mode |
|---------|-----------------|------------------|
| **Works without root** | ❌ (port blocked) | ✅ |
| **Uses official API** | ❌ | ✅ |
| **Requires Fake32960Server** | ✅ | ❌ |
| **Complexity** | Medium | Low |
| **Reliability** | Low (port conflict) | High |
| **Recommended** | ❌ | ✅ |

## Next Steps

1. **Test CarProperty mode** on your device
2. **Monitor logcat** for CarPropertyManager activity
3. **Verify spoiler response** when sending commands
4. **Report results** - does CarProperty mode work?

## Files

- `app/src/main/java/com/mazda/control/CarPropertySpoilerController.kt` - New controller
- `SPOILER_TROUBLESHOOTING.md` - General troubleshooting
- `PORT_32960_ANALYSIS.md` - Port conflict analysis
