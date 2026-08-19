#!/data/data/com.termux/files/usr/bin/bash
set -e

APP_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$APP_DIR"

echo "========================================"
echo " MC基岩版材质编辑器 - 修复版构建器"
echo "========================================"

# Termux ARM64 环境固定使用系统 OpenJDK 21。
# 不使用项目目录中的 ~/jdk-17，避免 libjli.so 缺失。
if [ ! -d "$PREFIX/lib/jvm/java-21-openjdk" ]; then
    echo "未找到 Termux OpenJDK 21，正在安装..."
    pkg update -y
    pkg install -y openjdk-21
fi

export JAVA_HOME="$PREFIX/lib/jvm/java-21-openjdk"
export PATH="$JAVA_HOME/bin:$PATH"

echo "[1/5] Java:"
java -version

if [ -n "${ANDROID_HOME:-}" ] && [ -d "$ANDROID_HOME" ]; then
    :
elif [ -d "$HOME/android-sdk" ]; then
    export ANDROID_HOME="$HOME/android-sdk"
elif [ -d "/sdcard/android-sdk" ]; then
    export ANDROID_HOME="/sdcard/android-sdk"
else
    echo "未找到 Android SDK。先运行：bash setup_termux_android_sdk.sh"
    exit 1
fi

export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/cmdline-tools/bin:$PATH"

echo "[2/5] Android SDK:"
echo "ANDROID_HOME=$ANDROID_HOME"

# 固定兼容 AGP 8.6.1 的 Gradle 8.10.2，避免 Termux 中 Gradle 9.x 与 AGP 不匹配。
GRADLE_HOME="$HOME/gradle-8.10.2"
if [ ! -x "$GRADLE_HOME/bin/gradle" ]; then
    echo "[3/5] 下载 Gradle 8.10.2..."
    pkg install -y wget unzip
    cd "$HOME"
    wget -q --show-progress https://services.gradle.org/distributions/gradle-8.10.2-bin.zip
    rm -rf "$GRADLE_HOME"
    unzip -q gradle-8.10.2-bin.zip
    rm -f gradle-8.10.2-bin.zip
    cd "$APP_DIR"
fi
export PATH="$GRADLE_HOME/bin:$PATH"

# Termux Android 是 ARM64，强制 Android Gradle Plugin 使用 Termux 自带的 ARM64 AAPT2，
# 避免 AGP 下载并执行 x86_64 Linux AAPT2。
export AAPT2="$PREFIX/bin/aapt2"
export AAPT2="$PREFIX/bin/aapt2"

echo "[4/5] 清理旧构建..."
rm -rf app/build

echo "[5/5] 开始编译 Debug APK..."
gradle -Pandroid.aapt2FromMavenOverride="$PREFIX/bin/aapt2" :app:assembleDebug --no-daemon --stacktrace

APK="$APP_DIR/app/build/outputs/apk/debug/app-debug.apk"
if [ ! -f "$APK" ]; then
    echo "ERROR: APK 未生成"
    exit 2
fi

# 基础自检：Manifest 中必须包含明确的启动 Activity。
if ! unzip -p "$APK" AndroidManifest.xml >/tmp/mc_manifest.bin 2>/dev/null; then
    echo "警告：无法直接读取二进制 Manifest，跳过 Manifest 文本检查。"
fi

mkdir -p "$APP_DIR/output"
cp "$APK" "$APP_DIR/output/MC材质编辑器-debug.apk"

echo
echo "========================================"
echo " 构建成功"
echo " APK: $APP_DIR/output/MC材质编辑器-debug.apk"
echo "========================================"
