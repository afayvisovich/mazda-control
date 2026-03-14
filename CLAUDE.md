# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Android application for controlling Mazda vehicle functions (trunk, spoiler, windows, HVAC) through a custom binary protocol. The app connects to the car's head unit via localhost:32960 and sends 512-byte command packets.

**Current Status**: Early development - basic Compose template with extensive protocol documentation.

## Build Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Run unit tests
./gradlew test

# Run instrumented tests (requires device/emulator)
./gradlew connectedAndroidTest

# Clean build
./gradlew clean

# Install debug APK on device
./gradlew installDebug
```

## Project Structure

```
app/
├── src/main/java/com/mazda/control/
│   ├── MainActivity.kt          # Main UI (Compose)
│   └── ui/theme/                # Material3 theming
├── src/main/res/                # Resources
└── build.gradle.kts             # Module config

analysis/                        # Protocol documentation (CRITICAL)
├── ANALYSIS.md                  # Complete protocol reference
├── SPOILER_APP_PLAN.md          # Implementation guide
├── PROPERTY_ID_MAPPING.md       # Property ID system
└── *.txt                        # Captured protocol logs
```

## Architecture

### Protocol Stack
```
┌─────────────────────────────────────┐
│  Android App (this project)         │
│  - Compose UI                       │
│  - Socket connection to :32960      │
└─────────────────┬───────────────────┘
                  │ TCP Socket
                  ▼
┌─────────────────────────────────────┐
│  Head Unit Server (localhost:32960) │
│  - Accepts 512-byte packets         │
│  - Routes to CarPropertyService     │
└─────────────────────────────────────┘
```

### Packet Structure (512 bytes)
```
Header (30 bytes):
  [0-1]   Magic: 0x23 0x23
  [2]     Command Mark: 0x02=action, 0x03=status
  [3]     Fixed: 0xFE
  [4-20]  VIN (17 bytes)
  [21]    Encryption mark: 0x01
  [22-23] Body length
  [24-29] Timestamp (YY MM DD HH MM SS)

RealBody (30-365):
  [30-33] Prefix: 01 02 03 01
  [34-37] Property ID: ALWAYS 0x00000000 (see note below)
  [38-45] Function Bytes: BF 11 0D [COMP] 27 [ACTION] 20 01
  [355]   Checksum/type
  [363]   End marker: 0x5A
  [365]   Command-specific byte
```

### Critical Architecture Note: Two-Level Property ID System

**Property IDs in packets are ALWAYS `0x00000000`**. Component identification happens through Function Byte[3] (byte 41 in RealBody):

| Component | Function Byte[3] | Android Framework Property ID |
|-----------|------------------|------------------------------|
| Driver window (FL) | `0x03` | `0x6600010C` |
| Passenger window (FR) | `0x01` | `0x6600010E` |
| Trunk | `0x07` | `0x66000210` |
| Spoiler | `0x06` | `0x6600022C` |
| HVAC | `0x12` | `0x66000023` |

The VHAL layer maps between Android Framework Property IDs and the protocol's internal format.

## Implementation Pattern

When implementing a control function:

1. **Read protocol documentation** in `analysis/ANALYSIS.md`
2. **Find the Function Bytes** for the component/action
3. **Build packet** with correct Component ID and Action Code
4. **Send via socket** to localhost:32960
5. **Parse response** (if needed, command mark=3)

### Example: Opening Trunk

```kotlin
// Function Bytes for trunk open: BF 11 0D 07 27 21 20 01
//                                  [prefix] [COMP=07] [ACTION=21]

val packet = ByteArray(512)
packet[0] = 0x23; packet[1] = 0x23  // Magic
packet[2] = 0x02                     // Command mark
packet[3] = 0xFE
// ... VIN, timestamp ...
packet[38] = 0xBF; packet[39] = 0x11; packet[40] = 0x0D
packet[41] = 0x07  // Component: trunk
packet[42] = 0x27
packet[43] = 0x21  // Action: open
packet[44] = 0x20; packet[45] = 0x01

socket.getOutputStream().write(packet)
```

## Protocol Documentation

**Primary reference**: `analysis/ANALYSIS.md` - Contains complete protocol specification with:
- All Property IDs and Component IDs
- Function Bytes for trunk, spoiler, windows (5 modes), HVAC
- Timing characteristics (trunk ~8.6s, spoiler ~2.2s, windows ~2.8-3.3s)
- Command/Response packet examples
- Troubleshooting guide

**Implementation guide**: `analysis/SPOILER_APP_PLAN.md` - Step-by-step guide for building a control app

## Development Workflow

### Adding New Control Function

1. Check `analysis/ANALYSIS.md` for Function Bytes
2. Create a controller class in `app/src/main/java/com/mazda/control/`
3. Implement packet generation (follow pattern in SPOILER_APP_PLAN.md)
4. Add UI controls in MainActivity.kt
5. Test on device/emulator (requires actual car connection for full testing)

### Testing

**Unit tests**: Protocol encoding/decoding tests can run without device
**Instrumented tests**: Require connection to car head unit at localhost:32960

### Debugging Protocol Issues

1. Check `analysis/ANALYSIS.md` section "Приложение B: Troubleshooting"
2. Verify Component ID (byte 41) and Action Code (byte 43)
3. Ensure Property ID in packet is `0x00000000` (not the Android Framework ID)
4. Check checksum bytes (355, 363, 365) - algorithm not fully reverse-engineered yet

## Known Limitations

**Not yet reverse-engineered**:
- Checksum algorithm (bytes 355, 363, 365)
- HVAC fan speed control
- HVAC temperature setting
- HVAC air distribution modes
- Encryption (Encrpy-mark=01, but decryption not needed for commands)

**Safety requirements**:
- Vehicle must be stationary (speed=0)
- Parking mode engaged for trunk/spoiler operations
- Commands should have confirmation dialogs

## Dependencies

- **Kotlin** 2.2.10
- **Compose BOM** 2024.09.00
- **Material3** (UI components)
- **Min SDK**: 30 (Android 11)
- **Target SDK**: 36

## Code Conventions

- Use Jetpack Compose for UI
- Follow Kotlin idiomatic patterns
- Keep packet generation logic in dedicated controller classes
- Log all sent/received packets for debugging
- Handle socket connection errors gracefully

## References

- `analysis/ANALYSIS.md` - Complete protocol specification
- `analysis/PROPERTY_ID_MAPPING.md` - Property ID system details
- `analysis/VALUE_ENCODING_ANALYSIS.md` - Value encoding research
- `analysis/*.txt` - Raw protocol captures from actual vehicle
