#!/bin/bash
set -e

# 代理配置
export http_proxy=http://127.0.0.1:10808
export https_proxy=http://127.0.0.1:10808
export HTTP_PROXY=http://127.0.0.1:10808
export HTTPS_PROXY=http://127.0.0.1:10808

# NDK 配置
export ANDROID_NDK=$HOME/ijkplayer-build/android-ndk-r21e
export ANDROID_SDK=$HOME/ijkplayer-build/android-sdk

NDK_BIN="$ANDROID_NDK/toolchains/llvm/prebuilt/linux-x86_64/bin"

# 目标架构
TARGET="aarch64-linux-android"
API_LEVEL=24
CC="$NDK_BIN/${TARGET}${API_LEVEL}-clang"
CXX="$NDK_BIN/${TARGET}${API_LEVEL}-clang++"
AR="$NDK_BIN/${TARGET}-ar"
STRIP="$NDK_BIN/${TARGET}-strip"

BUILD_DIR="$HOME/brotli-build"
BROTLI_SRC="$BUILD_DIR/brotli"
JNI_SRC="/mnt/e/Users/ASUS/Desktop/ReBiliClient/brotli_jni.cpp"
OUTPUT_SO="/mnt/e/Users/ASUS/Desktop/ReBiliClient/app/libs/arm64-v8a/libbrotli.so"

echo "=== Step 1: Clone Google brotli source ==="
mkdir -p "$BUILD_DIR"
cd "$BUILD_DIR"

if [ ! -d "$BROTLI_SRC" ]; then
    git clone --depth 1 https://github.com/google/brotli.git
    echo "brotli cloned"
else
    echo "brotli already exists"
fi

echo ""
echo "=== Step 2: Build brotli static libs for arm64-v8a ==="
cd "$BROTLI_SRC"

BROTLI_OUT="$BUILD_DIR/out"
# 清理旧的 .o 文件避免冲突
rm -rf "$BROTLI_OUT"
mkdir -p "$BROTLI_OUT"

# 收集所有 brotli C 源文件
BROTLI_C_SOURCES=$(find c/common c/dec c/enc -name "*.c" 2>/dev/null)

# 编译所有 .c 文件为 .o（使用路径前缀避免同名文件冲突）
OBJ_FILES=""
for src in $BROTLI_C_SOURCES; do
    # 将路径中的 / 替换为 _ 作为唯一文件名
    obj_name=$(echo "$src" | sed 's|/|_|g' | sed 's|\.c$|.o|')
    obj="$BROTLI_OUT/$obj_name"
    echo "Compiling $src -> $obj"
    $CC -c -O2 -fPIC -I c/include -o "$obj" "$src"
    OBJ_FILES="$OBJ_FILES $obj"
done

echo ""
echo "=== Step 3: Compile JNI wrapper ==="
JNI_OBJ="$BROTLI_OUT/brotli_jni.o"
$CXX -c -O2 -fPIC \
    -I "$BROTLI_SRC/c/include" \
    -I "$ANDROID_NDK/sysroot/usr/include" \
    -I "$ANDROID_NDK/sysroot/usr/include/aarch64-linux-android" \
    -o "$JNI_OBJ" "$JNI_SRC"
echo "JNI wrapper compiled"

echo ""
echo "=== Step 4: Link shared library ==="
mkdir -p "$(dirname "$OUTPUT_SO")"

$CC -shared -O2 -fPIC \
    -Wl,-soname,libbrotli.so \
    -o "$OUTPUT_SO" \
    $JNI_OBJ $OBJ_FILES \
    -llog -lm -lc

echo ""
echo "=== Step 5: Strip debug symbols ==="
$STRIP --strip-unneeded "$OUTPUT_SO"

echo ""
echo "=== Done ==="
echo "Output: $OUTPUT_SO"
ls -la "$OUTPUT_SO"
echo ""
echo "Exported symbols:"
$NDK_BIN/${TARGET}-nm -D "$OUTPUT_SO" | grep -E "Java_|JNI_OnLoad|Brotli" | head -20