#!/bin/bash
set -euo pipefail

ADB="$HOME/Library/Android/sdk/platform-tools/adb"
EMULATOR="$HOME/Library/Android/sdk/emulator/emulator"
APK="${1:-./app/build/outputs/apk/debug/app-debug.apk}"
PKG="dev.shreyaspatil.appfundemo.agent"
PRIV_APP_DIR="NotyAgentApp"
MODULE_ID="notyagentapp"

wait_for_boot() {
    $ADB wait-for-device
    until [ "$($ADB shell getprop sys.boot_completed | tr -d '\r')" = "1" ]; do
        sleep 2
    done
}

# Build
# chmod +x ./gradlew
# ./gradlew assembleDebug
[ -f "$APK" ] || { echo "APK not found: $APK"; exit 1; }

# Collect targets: AVDs (need booting) + already-connected ADB devices
options=()
types=()
ids=()

while IFS= read -r line; do
    [[ -n "$line" ]] && { options+=("AVD: $line"); types+=("avd"); ids+=("$line"); }
done < <($EMULATOR -list-avds)

while IFS=$'\t' read -r serial state; do
    [[ -z "$serial" || "$serial" == "List of devices attached" ]] && continue
    [[ "$state" != "device" ]] && continue
    options+=("Device: $serial"); types+=("device"); ids+=("$serial")
done < <($ADB devices)

[ ${#options[@]} -gt 0 ] || { echo "No AVDs or devices found"; exit 1; }

if [ ${#options[@]} -eq 1 ]; then
    idx=0
    echo "Only one target: ${options[0]}"
else
    PS3="Select target: "
    select choice in "${options[@]}" Quit; do
        [ "$choice" = "Quit" ] && exit 0
        [ -n "$choice" ] && { idx=$((REPLY - 1)); break; }
    done
fi

TYPE="${types[$idx]}"
ID="${ids[$idx]}"

# Boot AVD if needed
if [ "$TYPE" = "avd" ]; then
    killall qemu-system-aarch64 qemu-system-x86_64 2>/dev/null || true
    sleep 2

    $EMULATOR -avd "$ID" -writable-system -no-snapshot-load >> /tmp/emulator.log 2>&1 &

    echo "Waiting for emulator to register with adb..."
    SERIAL=""
    for _ in {1..60}; do
        SERIAL=$($ADB devices | awk '$2=="device" && $1 ~ /^emulator-/ {print $1; exit}')
        [ -n "$SERIAL" ] && break
        sleep 2
    done
    [ -n "$SERIAL" ] || { echo "Emulator did not appear in adb devices"; exit 1; }

    export ANDROID_SERIAL="$SERIAL"
    wait_for_boot
    echo "Emulator up: $ANDROID_SERIAL"
else
    export ANDROID_SERIAL="$ID"
    echo "Using connected device: $ANDROID_SERIAL"
fi

# Normal install — registers the package with PackageManager
$ADB install -t -r "$APK"

# Detect build type and branch
BUILD_TYPE=$($ADB shell getprop ro.build.type | tr -d '\r')
echo "ro.build.type = $BUILD_TYPE"

if [ "$BUILD_TYPE" = "userdebug" ] || [ "$BUILD_TYPE" = "eng" ]; then
    # ---------- userdebug / eng (emulators, dev builds) ----------
    echo "Using adb root + remount path"

    $ADB root
    $ADB disable-verity || true   # may already be disabled on -writable-system images
    $ADB reboot
    wait_for_boot

    $ADB root
    $ADB remount

    for P in /vendor/build.prop /system/build.prop /system_ext/build.prop /product/build.prop; do
        $ADB shell "[ -f $P ] && sed -i 's/ro.control_privapp_permissions=enforce/ro.control_privapp_permissions=log/g' $P" || true
    done

    $ADB shell mkdir -p "/system/priv-app/$PRIV_APP_DIR"
    $ADB push "$APK" "/system/priv-app/$PRIV_APP_DIR/$PRIV_APP_DIR.apk"
    $ADB shell chmod 755 "/system/priv-app/$PRIV_APP_DIR"
    $ADB shell chmod 644 "/system/priv-app/$PRIV_APP_DIR/$PRIV_APP_DIR.apk"
    $ADB shell restorecon -R "/system/priv-app/$PRIV_APP_DIR"

else
    # ---------- user build: requires Magisk, install as a Magisk module ----------
    echo "Production build — building and installing a Magisk module"

    # Confirm Magisk is present
    if ! $ADB shell "su -c 'magisk -v'" >/dev/null 2>&1; then
        echo "ERROR: Magisk not found via su on $ANDROID_SERIAL."
        echo "       On production builds, this script needs Magisk root."
        exit 1
    fi

    # Build the module locally
    MODULE_DIR=$(mktemp -d)
    trap 'rm -rf "$MODULE_DIR" "/tmp/${MODULE_ID}.zip"' EXIT

    mkdir -p "$MODULE_DIR/system/priv-app/$PRIV_APP_DIR"
    cp "$APK" "$MODULE_DIR/system/priv-app/$PRIV_APP_DIR/$PRIV_APP_DIR.apk"

    cat > "$MODULE_DIR/module.prop" <<EOF
id=$MODULE_ID
name=$PRIV_APP_DIR priv-app
version=1.0
versionCode=1
author=local
description=Installs $PKG to /system/priv-app and sets ro.control_privapp_permissions=log
EOF

    # Magisk applies system.prop via resetprop early in boot — handles ro.* too.
    cat > "$MODULE_DIR/system.prop" <<EOF
ro.control_privapp_permissions=log
EOF

    # Minimal META-INF so the zip is also recovery-flashable, not just CLI-installable
    mkdir -p "$MODULE_DIR/META-INF/com/google/android"
    echo "#MAGISK" > "$MODULE_DIR/META-INF/com/google/android/updater-script"
    cat > "$MODULE_DIR/META-INF/com/google/android/update-binary" <<'EOF'
#!/sbin/sh
umask 022
OUTFD=$2
ZIPFILE=$3
mount /data 2>/dev/null
[ -f /data/adb/magisk/util_functions.sh ] || { echo "! Magisk not installed"; exit 1; }
. /data/adb/magisk/util_functions.sh
install_module
exit 0
EOF
    chmod +x "$MODULE_DIR/META-INF/com/google/android/update-binary"

    ZIP_PATH="/tmp/${MODULE_ID}.zip"
    (cd "$MODULE_DIR" && zip -rq "$ZIP_PATH" .)
    echo "Built module: $ZIP_PATH"

    REMOTE_ZIP="/sdcard/Download/${MODULE_ID}.zip"
    $ADB push "$ZIP_PATH" "$REMOTE_ZIP"

    echo "Installing module via Magisk..."
    $ADB shell "su -c 'magisk --install-module $REMOTE_ZIP'"
fi

# Reboot so PackageManager re-scans /system/priv-app under the new enforcement mode
$ADB reboot
wait_for_boot

# Verify
echo ""
echo "=== Verification ==="
echo -n "ro.control_privapp_permissions = "
$ADB shell getprop ro.control_privapp_permissions
echo ""
echo "Permission state for $PKG:"
$ADB shell dumpsys package "$PKG" 2>/dev/null | grep -E "EXECUTE_APP_FUNCTIONS" || echo "  (no EXECUTE_APP_FUNCTIONS line)"
echo ""
if $ADB shell dumpsys package "$PKG" 2>/dev/null | grep -q "EXECUTE_APP_FUNCTIONS.*granted=true"; then
    echo "✓ EXECUTE_APP_FUNCTIONS granted on $ANDROID_SERIAL"
else
    echo "✗ EXECUTE_APP_FUNCTIONS NOT granted"
    echo "  - getprop above should be 'log'"
    if [ "$BUILD_TYPE" = "user" ]; then
        echo "  - check module: adb shell su -c 'ls -la /data/adb/modules/$MODULE_ID/'"
        echo "  - check overlay applied: adb shell ls -la /system/priv-app/$PRIV_APP_DIR/"
    else
        echo "  - check APK at /system/priv-app/$PRIV_APP_DIR/$PRIV_APP_DIR.apk"
    fi
fi