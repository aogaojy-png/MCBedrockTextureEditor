#!/data/data/com.termux/files/usr/bin/bash
set -e
DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$DIR"
bash setup_termux_android_sdk.sh
source "$HOME/.mcpack_android_env"
bash build_termux.sh
