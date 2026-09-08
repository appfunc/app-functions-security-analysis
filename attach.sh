#!/bin/zsh
# Attach helper script (waiting for :search process before attaching to PID)
# Attaches the hook_gemini.js script on a rooted Pixel 10 Pro.
# Used on the google assistant app (com.google.android.googlequicksearchbox)

# EXACT tested version
#    android:versionCode="301744903"
#    android:versionName="17.24.28.sa.arm64"

SCRIPT="$(dirname "$0")/hook_gemini.js"
PKG="com.google.android.googlequicksearchbox"
FS_BIN="/data/local/tmp/frida-server"

echo "Restarting frida-server (root)..."
adb shell "su -c 'pkill frida-server 2>/dev/null || true; sleep 0.5; $FS_BIN -D &'" || true
sleep 2

echo "Force-stopping all Gemini processes..."
adb shell am force-stop "$PKG"
sleep 2

echo "Polling for :search process..."
echo "Wait for Frida to attach post arbitrary sleep"
PID=""
for i in $(seq 1 300); do
    PID=$(adb shell ps -A 2>/dev/null \
        | grep "${PKG}:search" \
        | awk '{print $2}' \
        | head -1 \
        | tr -d '\r\n')
    if [ -n "$PID" ]; then
        echo "Found :search PID=$PID"
        sleep 5 # indication of a GC cycle that exists in the app, arbitrary wait time from trial and error (seems to help)
        echo "[SUCCESS] Attaching to PID=$PID..."
        frida --runtime=v8 -U -p "$PID" -l "$SCRIPT"
        exit 0
    fi
    sleep 0.3
done

echo "[ERROR] :search process never appeared"
exit 1
