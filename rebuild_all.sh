#!/bin/bash
set -e

# 代理配置
export http_proxy=http://127.0.0.1:10808
export https_proxy=http://127.0.0.1:10808
export HTTP_PROXY=http://127.0.0.1:10808
export HTTPS_PROXY=http://127.0.0.1:10808

export ANDROID_NDK=$HOME/ijkplayer-build/android-ndk-r21e
export ANDROID_SDK=$HOME/ijkplayer-build/android-sdk

cd ~/ijkplayer-build/ijkplayer

# 修复 NDK r21 兼容性
APP_MK=android/ijkplayer/ijkplayer-arm64/src/main/jni/Application.mk
sed -i 's/APP_STL := stlport_static/APP_STL := c++_static/g' $APP_MK 2>/dev/null || true
sed -i 's/NDK_TOOLCHAIN_VERSION=4.9/NDK_TOOLCHAIN_VERSION=clang/g' $APP_MK 2>/dev/null || true
sed -i '/APP_ALLOW_MISSING_DEPS/d' $APP_MK 2>/dev/null || true
sed -i 's/-Wno-psabi//g' $APP_MK 2>/dev/null || true

# 修复 NDK 版本检测
sed -i 's/11\*|12\*|13\*|14\*/11*|12*|13*|14*|21*/g' android/contrib/tools/do-detect-env.sh 2>/dev/null || true

echo "=== Step 1: Init OpenSSL source ==="
./init-android-openssl.sh

echo ""
echo "=== Step 2: Compile OpenSSL for arm64 ==="
cd android/contrib
./compile-openssl.sh arm64

echo ""
echo "=== Step 3: Recompile FFmpeg for arm64 (with HTTPS/SSL) ==="
# 清理旧的 FFmpeg 构建
rm -rf build/ffmpeg-arm64 2>/dev/null
./compile-ffmpeg.sh arm64

echo ""
echo "=== Step 3: Recompile ijkplayer for arm64 ==="
cd ~/ijkplayer-build/ijkplayer
# 清理旧的构建产物
rm -rf ijkplayer/ijkplayer-arm64/src/main/obj 2>/dev/null
rm -rf ijkplayer/ijkplayer-arm64/src/main/libs 2>/dev/null
cd android
./compile-ijk.sh arm64

echo ""
echo "=== Step 4: Copy .so files to project ==="
DEST=/mnt/e/Users/ASUS/Desktop/ReBiliClient/app/libs/arm64-v8a
mkdir -p "$DEST"
cp ~/ijkplayer-build/ijkplayer/android/contrib/build/ffmpeg-arm64/output/lib/libijkffmpeg.so "$DEST/" 2>/dev/null || echo "libijkffmpeg.so not found, checking..."
find ~/ijkplayer-build/ijkplayer -name 'libijkffmpeg.so' -path '*/arm64*' -exec cp {} "$DEST/" \;
find ~/ijkplayer-build/ijkplayer -name 'libijkplayer.so' -path '*/arm64*' -exec cp {} "$DEST/" \;
find ~/ijkplayer-build/ijkplayer -name 'libijksdl.so' -path '*/arm64*' -exec cp {} "$DEST/" \;

echo ""
echo "=== Done ==="
ls -la "$DEST/"*.so

echo ""
echo "=== Verify HTTPS support ==="
grep -E 'CONFIG_HTTPS|CONFIG_TLS|CONFIG_OPENSSL' ~/ijkplayer-build/ijkplayer/android/contrib/ffmpeg-arm64/config.h