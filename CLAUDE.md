# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Android application for controlling Mazda car functions (spoiler, windows, HVAC, locks) via the head unit's proprietary services. Uses Shizuku to call system services without root.

**Target:** Android Automotive (Head Unit with Mazda's IVI system)
**Min SDK:** 30 (Android 11)
**Build System:** Gradle Kotlin DSL

## Build Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Run unit tests
./gradlew test

# Run instrumented tests
./gradlew connectedAndroidTest

# Clean build
./gradlew clean

# Single test class
./gradlew test --tests "com.mazda.control.ExampleTest"
```

## Architecture

### Mock/Real Mode Architecture

`TestMode` is the central switcher that selects between:
- **Mock Mode** (default): Uses `MockMegaController` for development on emulators without Shizuku
- **Real Mode**: Uses `RealMegaController` which calls `mega.controller` service via Shizuku

All car control goes through `IMegaController` interface:
```kotlin
interface IMegaController {
    fun callService(code: Int, propId: Int, value: Int): Boolean
    fun getProperty(propId: Int): Int
    fun setProperty(propId: Int, value: Int): Boolean
    fun setSpoiler(position: Int): Boolean  // 0=CLOSE, 1=OPEN, 2=STOP
    fun getStats(): Map<String, Any>
}
```

### Shizuku Integration

Shizuku enables calling system services (`service call <name> <code> ...`) without root:
- `ShizukuBinderCaller` - makes binder calls to car services
- `ShizukuShellExecutor` - executes shell commands via Shizuku
- `ShizukuIntegrationHelper` - manages permissions and status

The app communicates with `mega.controller` service via Shizuku on the head unit.

### Risk Mitigation

`TBoxProtocolController` includes protective patterns:
- `CircuitBreaker` - prevents cascading failures
- `SystemHealthMonitor` - monitors system health
- `SafeServiceCaller` - timeout and retry logic

### Protocol

The app reverse-engineered Mazda's GB32960-based protocol. Key findings in `analysis/docs/`:
- **Byte 355**: Sequence counter (decrements 1/sec, commands use fixed 0x2B)
- **Byte 363**: Command marker (0x5A = command, 0x0A = status poll)
- **Byte 365**: Action (0x00 = OPEN, 0x10 = CLOSE)
- **Property ID for spoiler**: 0x6600022C (or variants 0x38, 0x660000c3)

## Key Files

| File | Purpose |
|------|---------|
| `MainActivity.kt` | Main UI with Compose, mode switching |
| `TestMode.kt` | Mock/Real mode switcher |
| `RealMegaController.kt` | Real Shizuku-based controller |
| `TBoxProtocolController.kt` | Protocol execution with risk mitigation |
| `ShizukuBinderCaller.kt` | Shizuku binder call wrapper |
| `PacketGenerator.kt` | GB32960 packet generation |

## Shizuku Setup for Real Mode

1. Install Shizuku on head unit
2. Authorize this app in Shizuku settings
3. Switch to Real Mode via UI toggle
4. The app will call `mega.controller` service via Shizuku

## Key Property IDs

- **Spoiler**: `0x6600022C` (VARIANT_3), `0x38` (VARIANT_2), `0x660000c3` (VARIANT_1)
- See `SpoilerPropertyMode.kt` and `CARPROPERTY_MODE_GUIDE.md`

## Analysis Documentation

`analysis/docs/` contains reverse engineering findings:
- `01-start/355_COMPLETE_GUIDE.md` - Byte 355 counter algorithm
- `03-protocol/reverse-engineering.md` - GB32960 protocol structure
- `02-architecture/` - Mazda IVI system architecture analysis
