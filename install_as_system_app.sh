#!/bin/bash
set -euo pipefail

ADB="$HOME/Library/Android/sdk/platform-tools/adb"
EMULATOR="$HOME/Library/Android/sdk/emulator/emulator"
APK="./app/build/outputs/apk/debug/app-debug.apk"

wait_for_boot() {
    $ADB wait-for-device
    until [ "$($ADB shell getprop sys.boot_completed | tr -d '\r')" = "1" ]; do
        sleep 2
    done
}

# Build
chmod +x ./gradlew
./gradlew assembleDebug
[ -f "$APK" ] || { echo "APK not found: $APK"; exit 1; }

# Pick AVD
avds=()
while IFS= read -r line; do
    [[ -n "$line" ]] && avds+=("$line")
done < <($EMULATOR -list-avds)
[ ${#avds[@]} -gt 0 ] || { echo "No AVDs found"; exit 1; }

if [ ${#avds[@]} -eq 1 ]; then
    AVD="${avds[0]}"
else
    PS3="Select emulator: "
    select AVD in "${avds[@]}" Quit; do
        [ "$AVD" = "Quit" ] && exit 0
        [ -n "$AVD" ] && break
    done
fi

# Kill existing
killall qemu-system-aarch64 qemu-system-x86_64 2>/dev/null || true
sleep 2

# Boot
$EMULATOR -avd "$AVD" -writable-system -no-snapshot-load >> /tmp/emulator.log 2>&1 &
wait_for_boot
echo "Emulator up"

# Initial install (registers package with PackageManager)
$ADB install -t -r "$APK"

# Disable verified boot
$ADB root
$ADB disable-verity
$ADB reboot
wait_for_boot

# Push to system
$ADB root
$ADB remount
$ADB shell mkdir -p /system/priv-app/NotyAgentApp
$ADB push "$APK" /system/priv-app/NotyAgentApp/NotyAgentApp.apk

$ADB reboot
wait_for_boot
echo "Done — app installed as system/priv-app"