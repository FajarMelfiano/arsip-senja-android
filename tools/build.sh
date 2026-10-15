#!/usr/bin/env bash
# Pembungkus build: mengunci JAVA_HOME/ANDROID_HOME ke instalasi lokal
# sehingga `./gradlew` bisa dijalankan tanpa Android Studio.
set -euo pipefail
export JAVA_HOME="$HOME/tools/jdk-17.0.13+11"
export ANDROID_HOME="$HOME/android-sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$JAVA_HOME/bin:$PATH"
cd "$(dirname "$0")/.."
exec ./gradlew "$@"
