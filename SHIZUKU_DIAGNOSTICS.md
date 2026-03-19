# 🔍 Shizuku Diagnostics Guide

## Problem: Application shows "Shizuku not running" but Shizuku is installed

This guide helps diagnose Shizuku connection issues on Mazda Head Unit.

---

## 📋 How Application Finds Shizuku

### Standard Detection Flow

1. **Shizuku.pingBinder()** - Checks if Shizuku Binder service is available
2. **Shizuku.getVersion()** - Gets Shizuku API version
3. **Shizuku.getUid()** - Gets Shizuku process UID (should be 0 for root)
4. **Shizuku.checkSelfPermission()** - Checks if app is authorized (0=granted, -1=not authorized)

### Package Name Search

Application now searches for these package names (in order):
1. `moe.shizuku.privileged.api` - Standard Shizuku
2. `moe.shizuku.manager` - Shizuku Manager
3. `rikka.shizuku` - Rikka Shizuku
4. `com.android.shell` - Built-in Android Shell (may have Shizuku)
5. `android` - System Android

If none found, scans all installed packages for "shizuku" or "privileged" keywords.

---

## 🛠️ Diagnostic Commands

### 1. Check if Shizuku is Installed

```bash
# Standard package
adb shell pm list packages | grep shizuku

# All packages with "privileged"
adb shell pm list packages | grep privileged

# Full package list (search manually)
adb shell pm list packages > all_packages.txt
```

### 2. Check if Shizuku is Running

```bash
# Check processes
adb shell ps | grep shizuku
adb shell ps | grep privileged

# Check services
adb shell dumpsys activity services | grep -i shizuku

# Check binder services
adb shell dumpsys platform service | grep -i binder
```

### 3. Start Shizuku (if not running)

```bash
# Standard Shizuku
adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh

# Wait for initialization
sleep 2

# Verify it's running
adb shell ps | grep shizuku
```

### 4. Check Application Authorization

```bash
# View logcat when app starts
adb logcat -s ShizukuChecker:*
adb logcat -s ShizukuHelper:*
adb logcat -s MainActivity:*
```

Expected output:
```
🔍 Shizuku Diagnosis:
====================
🏓 Ping Binder: true
📦 Version: 13
🔐 UID: 0
🔑 Permission: 0 (0=granted, -1=not authorized)
✅ Shizuku fully operational
```

### 5. Check for Built-in Shizuku

Some Head Units have built-in Shizuku-like functionality:

```bash
# Check system services
adb shell dumpsys platform service | grep -E "mega|controller|carservice"

# Check if mega.controller service exists
adb shell dumpsys platform service | grep mega.controller
```

---

## ⚠️ Common Issues

### Issue 1: "binder haven't been received"

**Symptom:** App throws `IllegalStateException: binder haven't been received`

**Cause:** Shizuku binder not initialized yet when app starts

**Solution:**
- App now has retry logic (5 attempts, 500ms delay + 1 second initial delay)
- Wait 2-3 seconds after Shizuku start before launching app

### Issue 2: "Shizuku not installed" but it is

**Symptom:** App shows "Shizuku not installed" but `adb shell pm list packages | grep shizuku` shows it

**Possible Causes:**
1. **Different package name** - Head Unit uses custom Shizuku build
2. **Built-in Shizuku** - Integrated into system, different detection needed
3. **Permission denied** - App can't query package manager

**Solution:**
```bash
# Find exact package name
adb shell pm list packages | grep -i "shizuku\|privileged"

# Check if app can query packages
adb shell dumpsys package <com.mazda.control>
```

### Issue 3: "Not authorized" after opening Shizuku app

**Symptom:** User opens Shizuku app and authorizes, but app still shows "Not authorized"

**Cause:** App needs to re-check permission after authorization

**Solution:**
1. Authorize app in Shizuku settings
2. **Force stop and restart** MazdaControl app
3. Or use the "Request Permission" button in app

---

## 🔧 Manual Testing

### Test Binder Connection

```kotlin
// In MainActivity or anywhere
ShizukuBinderChecker.diagnose()
```

### Test with Retry

```kotlin
// Wait for binder with retries
val ready = ShizukuBinderChecker.waitForBinder(
    maxAttempts = 10,
    delayMs = 500
)
Log.d("Test", "Binder ready: $ready")
```

### Check All Possible Packages

```kotlin
val context = applicationContext
val packages = listOf(
    "moe.shizuku.privileged.api",
    "moe.shizuku.manager",
    "rikka.shizuku",
    "com.android.shell"
)

for (pkg in packages) {
    try {
        context.packageManager.getPackageInfo(pkg, 0)
        Log.d("Test", "✅ Found: $pkg")
    } catch (e: Exception) {
        Log.d("Test", "❌ Not found: $pkg")
    }
}
```

---

## 📊 Diagnostic Output Interpretation

| Output | Meaning | Action |
|--------|---------|--------|
| `🏓 Ping Binder: false` | Shizuku not running | Start Shizuku with start.sh |
| `📦 Version: 13` | Shizuku API v13 | ✅ Normal |
| `🔐 UID: 0` | Running as root | ✅ Normal |
| `🔑 Permission: 0` | App authorized | ✅ Ready to use |
| `🔑 Permission: -1` | App not authorized | Open Shizuku settings |
| `❌ Binder not ready` | Shizuku initializing | Wait and retry |

---

## 🚀 Quick Diagnostic Script

Run on Head Unit via ADB:

```bash
#!/bin/bash
echo "🔍 Shizuku Diagnostic"
echo "===================="

echo -n "1. Package installed: "
adb shell pm list packages | grep -q "moe.shizuku.privileged.api" && echo "✅ Yes" || echo "❌ No"

echo -n "2. Process running: "
adb shell ps | grep -q "shizuku" && echo "✅ Yes" || echo "❌ No"

echo -n "3. Binder service: "
adb shell dumpsys activity services | grep -q "shizuku" && echo "✅ Yes" || echo "❌ No"

echo ""
echo "📋 Recent logs:"
adb logcat -d | grep -i "shizuku" | tail -10
```

---

## 📝 Next Steps

1. **Run diagnostic script** on Head Unit
2. **Check logcat** when app starts
3. **Verify Shizuku is running** with `ps | grep shizuku`
4. **Authorize app** in Shizuku settings
5. **Force stop and restart** app
6. **Check logs** for "✅ Shizuku fully operational"

If still not working:
- Try **built-in Shizuku** detection
- Check for **custom ROM modifications**
- Verify **SELinux policy** allows binder calls
