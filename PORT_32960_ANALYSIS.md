# Port 32960 Conflict Analysis

## Problem

Diagnostic logs show that port 32960 is **unavailable** for the MazdaControl app, even though the original Huron RMU app uses this port internally.

## Root Cause

### Fake32960Server Exists in Original App

Analysis of decompiled Huron RMU reveals:
- `com.mega.rmu.tbox.Fake32960Server` - **internal server** that listens on port 32960
- This server is used by CarPropertyService to receive JSON commands
- The server is **embedded** in the original app's process space

### Why Connection Fails

1. **Internal IPC**: Fake32960Server likely uses **internal IPC** (Binder/localhost) that's only accessible from within the original app's process
2. **Firewall/SELinux**: Android's security model prevents external apps from connecting to another app's localhost sockets
3. **Race Condition**: Multiple clients trying to connect to the same port causes conflicts

## Evidence from Logs

```
03-17 14:23:03.813  8936  9074 I CallServerInterceptor: request <3 ip = localhost/127.0.0.1 port = 32960>
03-17 14:23:03.813  8936  9139 I SelectorReader: read data from server[...]
03-17 14:23:03.814  8936  9132 I ResponseParserInterceptor: upload stick : fail-----
```

**Key observations:**
- Original app (PID 8936) successfully connects to localhost:32960
- Data is sent and received (SelectorReader processes response)
- **But** the command fails with "error != OK"
- This suggests the server is working, but our external connection is blocked

## Solution Options

### Option 1: Use Different Port (RECOMMENDED)

Create a **standalone proxy server** that runs on a different port and forwards to CarPropertyService:

```kotlin
// New server on port 32961
class SpoilerProxyServer : ServerSocket(32961) {
    override fun accept(): Socket {
        // Forward JSON commands to CarPropertyService via Binder
        // No root required - uses official Android IPC
    }
}
```

**Pros:**
- No conflict with original app
- Works without root
- Clean separation

**Cons:**
- Requires implementing Binder IPC to CarPropertyService

### Option 2: Hook into Existing Server

Use **Xposed/Frida** to inject into Huron RMU process and add our command handler:

```java
// Xposed module
findClass("com.mega.rmu.tbox.Fake32960Server", classLoader)
    .makeMethod("onReceive", ...).hook(...)
```

**Pros:**
- Direct access to existing server
- Can intercept all commands

**Cons:**
- Requires Xposed/Frida (may void warranty)
- Complex implementation
- Security risks

### Option 3: Use Original App's Protocol

Reverse-engineer the **exact JSON format** that HuronCarSettings uses and send it via **broadcast intent** or **AIDL**:

```kotlin
// Send via broadcast (if CarPropertyService listens)
val intent = Intent("com.mega.carservice.SPOILER_COMMAND").apply {
    putExtra("value", 1) // OPEN
    putExtra("propertyId", 0x38)
}
sendBroadcast(intent)
```

**Pros:**
- No port conflicts
- Uses official Android mechanisms

**Cons:**
- Need to discover the correct intent action
- May require signature permission

## Recommended Next Steps

1. **Analyze CarPropertyService** - Find how it communicates with Fake32960Server
2. **Check for AIDL interfaces** - Look for official IPC mechanisms
3. **Monitor network traffic** - Use Wireshark/tcpdump to see what format is actually sent
4. **Try port 32961** - Test if a different port works

## Diagnostic Commands

```bash
# Check what's listening on port 32960
adb shell netstat -tlnp | grep 32960

# Check SELinux status
adb shell getenforce

# Try connecting from adb shell
adb shell nc localhost 32960

# Monitor all network activity
adb shell tcpdump -i lo -w /sdcard/lo.pcap
```

## Conclusion

**Port 32960 is NOT the solution** - it's already used by the original app's internal IPC mechanism. We need to either:

1. **Use a different port** with our own proxy server
2. **Find the official IPC mechanism** (AIDL/Binder)
3. **Hook into the existing server** with Xposed

The JSON mode approach is still valid, but we need a different transport mechanism.
