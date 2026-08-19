#!/data/data/com.termux/files/usr/bin/bash
set -e
APK="${1:-app/build/outputs/apk/debug/app-debug.apk}"
if [ ! -f "$APK" ]; then
  echo "找不到 APK: $APK"
  exit 1
fi
echo "APK: $APK"
echo "检查 ZIP/APK 结构..."
unzip -t "$APK" >/dev/null
echo "OK: APK ZIP 结构正常"
echo "检查 DEX 中的 MainActivity..."
if unzip -p "$APK" classes.dex | strings | grep -q 'com/k9t2/mcpackmaker/MainActivity'; then
  echo "OK: MainActivity 已进入 classes.dex"
else
  echo "ERROR: MainActivity 不在 classes.dex"
  exit 2
fi
echo "检查完成。"
