#!/bin/bash
set -e

export http_proxy=http://127.0.0.1:10808
export https_proxy=http://127.0.0.1:10808
export HTTP_PROXY=http://127.0.0.1:10808
export HTTPS_PROXY=http://127.0.0.1:10808

export ANDROID_NDK=$HOME/ijkplayer-build/android-ndk-r21e
export ANDROID_SDK=$HOME/ijkplayer-build/android-sdk

cd ~/ijkplayer-build/ijkplayer

echo "=== Compile ijkplayer native libs for arm64 ==="
# 修复 NDK r21 兼容性
APP_MK=android/ijkplayer/ijkplayer-arm64/src/main/jni/Application.mk
sed -i 's/APP_STL := stlport_static/APP_STL := c++_static/g' $APP_MK
sed -i 's/NDK_TOOLCHAIN_VERSION=4.9/NDK_TOOLCHAIN_VERSION=clang/g' $APP_MK
# 移除 APP_ALLOW_MISSING_DEPS（soundtouch 已初始化）
sed -i '/APP_ALLOW_MISSING_DEPS/d' $APP_MK
echo "APP_MK patched"

# 清理之前的构建产物
rm -rf ijkplayer/ijkplayer-arm64/src/main/obj 2>/dev/null
rm -rf ijkplayer/ijkplayer-arm64/src/main/libs 2>/dev/null

cd android
./compile-ijk.sh arm64

echo ""
echo "=== ijkplayer native libs done ==="
find ~/ijkplayer-build/ijkplayer/android/contrib/build/ -name '*.so' -path '*/arm64*' 2>/dev/null