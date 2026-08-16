#!/bin/bash
set -e

export http_proxy=http://127.0.0.1:10808
export https_proxy=http://127.0.0.1:10808

NDK_VERSION="android-ndk-r21e"
NDK_ZIP="${NDK_VERSION}-linux-x86_64.zip"
NDK_URL="https://dl.google.com/android/repository/${NDK_ZIP}"

cd ~/ijkplayer-build

echo "=== Downloading NDK r21e ==="
if [ ! -f "$NDK_ZIP" ]; then
    curl -L -o "$NDK_ZIP" "$NDK_URL"
else
    echo "NDK zip already exists, skipping download"
fi

echo "=== Extracting NDK ==="
if [ ! -d "$NDK_VERSION" ]; then
    unzip -q "$NDK_ZIP"
fi

echo "NDK ready at: $HOME/ijkplayer-build/$NDK_VERSION"
ls "$NDK_VERSION/toolchains/llvm/prebuilt/linux-x86_64/bin/" | head -5