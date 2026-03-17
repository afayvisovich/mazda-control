#!/bin/bash

# Port Scanner for Mazda Control
# Scans common ports to find available services

echo "=== Mazda Control Port Scanner ==="
echo ""

# Common ports to check
PORTS=(32960 32961 32962 5000 8080 8888 9000 4444 5555)

echo "Checking ports on device..."
echo ""

for PORT in "${PORTS[@]}"; do
    RESULT=$(adb shell "nc -z -w1 localhost $PORT 2>&1 && echo OPEN || echo CLOSED")
    if [[ "$RESULT" == *"OPEN"* ]]; then
        echo "✓ Port $PORT: OPEN"
        
        # Try to get more info
        INFO=$(adb shell "netstat -tlnp 2>/dev/null | grep :$PORT" | head -1)
        if [ ! -z "$INFO" ]; then
            echo "  └─ $INFO"
        fi
    else
        echo "✗ Port $PORT: CLOSED"
    fi
done

echo ""
echo "=== Full netstat output ==="
adb shell netstat -tlnp 2>/dev/null | grep -E "LISTEN|tcp" | head -20

echo ""
echo "=== SELinux Status ==="
adb shell getenforce 2>/dev/null || echo "Cannot determine SELinux status"

echo ""
echo "=== Running Processes (partial) ==="
adb shell ps -A 2>/dev/null | grep -E "mega|car|rmu" | head -10
