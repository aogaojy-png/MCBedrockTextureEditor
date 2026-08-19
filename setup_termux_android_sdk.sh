#!/data/data/com.termux/files/usr/bin/bash
set -e

echo "========================================"
echo " MC材质编辑器 - Android SDK 初始化"
echo "========================================"

pkg update -y
pkg install -y wget unzip

if [ -d "$PREFIX/lib/jvm/java-21-openjdk" ]; then
    export JAVA_HOME="$PREFIX/lib/jvm/java-21-openjdk"
elif [ -d "$PREFIX/lib/jvm/java-17-openjdk" ]; then
    export JAVA_HOME="$PREFIX/lib/jvm/java-17-openjdk"
else
    pkg install -y openjdk-17
    export JAVA_HOME="$PREFIX/lib/jvm/java-17-openjdk"
fi
export PATH="$JAVA_HOME/bin:$PATH"

SDK="$HOME/android-sdk"
CMD="$SDK/cmdline-tools/latest"
mkdir -p "$SDK"

if [ ! -x "$CMD/bin/sdkmanager" ]; then
    cd "$HOME"
    wget -q --show-progress https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O cmdline-tools.zip
    rm -rf "$SDK/cmdline-tools"
    mkdir -p "$SDK/cmdline-tools/tmp"
    unzip -q cmdline-tools.zip -d "$SDK/cmdline-tools/tmp"
    mkdir -p "$SDK/cmdline-tools/latest"
    mv "$SDK/cmdline-tools/tmp/cmdline-tools/"* "$SDK/cmdline-tools/latest/"
    rm -rf "$SDK/cmdline-tools/tmp" cmdline-tools.zip
fi

export ANDROID_HOME="$SDK"
export ANDROID_SDK_ROOT="$SDK"
export PATH="$CMD/bin:$SDK/platform-tools:$PATH"

yes | sdkmanager --licenses >/dev/null 2>&1 || true
sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"

cat > "$HOME/.mcpack_android_env" <<EOF
export JAVA_HOME="$JAVA_HOME"
export ANDROID_HOME="$SDK"
export ANDROID_SDK_ROOT="$SDK"
export PATH="\$JAVA_HOME/bin:\$ANDROID_HOME/cmdline-tools/latest/bin:\$ANDROID_HOME/platform-tools:\$PATH"
EOF

echo "SDK 初始化完成。"
