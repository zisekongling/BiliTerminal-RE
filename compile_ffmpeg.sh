#!/bin/bash
set -e

export http_proxy=http://127.0.0.1:10808
export https_proxy=http://127.0.0.1:10808
export HTTP_PROXY=http://127.0.0.1:10808
export HTTPS_PROXY=http://127.0.0.1:10808

export ANDROID_NDK=$HOME/ijkplayer-build/android-ndk-r21e
export ANDROID_SDK=$HOME/ijkplayer-build/android-sdk

cd ~/ijkplayer-build/ijkplayer

IJK_FFMPEG_COMMIT=ff4.0--ijk0.8.8--20210426--001

echo "=== Step 1: Clone FFmpeg arm64 (shallow) ==="
if [ ! -d "android/contrib/ffmpeg-arm64" ]; then
    mkdir -p android/contrib
    git clone --depth 1 --branch $IJK_FFMPEG_COMMIT https://github.com/Bilibili/FFmpeg.git android/contrib/ffmpeg-arm64
    echo "FFmpeg arm64 cloned"
else
    echo "FFmpeg arm64 already exists"
fi

echo ""
echo "=== Step 2: Init config ==="
if [ ! -f "android/contrib/tools/do-compile-ffmpeg.sh" ]; then
    ./init-config.sh
    ./init-android-libyuv.sh
    ./init-android-soundtouch.sh
fi
echo "Config done"

echo ""
echo "=== Step 2.5: Patch NDK version check for r21 ==="
sed -i 's/11\*|12\*|13\*|14\*/11*|12*|13*|14*|21*/g' android/contrib/tools/do-detect-env.sh
echo "Patched"

echo ""
echo "=== Step 3: Compile FFmpeg for arm64-v8a ==="
cd android/contrib
# 清理之前失败的构建
rm -rf build/ffmpeg-arm64 2>/dev/null
./compile-ffmpeg.sh arm64

echo ""
echo "=== FFmpeg arm64 done ==="
ls -la build/ffmpeg-arm64/output/lib/*.so 2>/dev/null