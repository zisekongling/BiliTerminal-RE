/**
 * Brotli JNI wrapper - 为 com.netease.hearttouch.brotlij 提供 native 实现
 * 使用 Google brotli C 库进行压缩/解压，通过 RegisterNatives 动态注册
 */

#include <jni.h>
#include <android/log.h>
#include <brotli/encode.h>
#include <brotli/decode.h>
#include <cstring>
#include <cstdlib>
#include <cstdio>

#define LOG_TAG "brotli-jni"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// 解压时的输出缓冲区大小
static const size_t DECOMPRESS_OUTPUT_BUFFER_SIZE = 64 * 1024;

// ===================== Compressor JNI =====================

/**
 * 创建 Brotli 压缩器实例
 * 返回 BrotliEncoderState 指针作为句柄
 */
static jlong nativeCreateBrotliCompressorInstance(JNIEnv *env, jclass clazz,
        jint mode, jint quality, jint lgwin, jint lgblock) {
    BrotliEncoderState *state = BrotliEncoderCreateInstance(nullptr, nullptr, nullptr);
    if (state) {
        BrotliEncoderSetParameter(state, BROTLI_PARAM_MODE, mode);
        BrotliEncoderSetParameter(state, BROTLI_PARAM_QUALITY, quality);
        BrotliEncoderSetParameter(state, BROTLI_PARAM_LGWIN, lgwin);
        BrotliEncoderSetParameter(state, BROTLI_PARAM_LGBLOCK, lgblock);
    }
    return reinterpret_cast<jlong>(state);
}

/**
 * 压缩数据块
 * 返回压缩后的字节数，0 表示需要更多输入，-1 表示出错
 */
static jint nativeCompress(JNIEnv *env, jclass clazz, jlong encoderInstance,
        jbyteArray data, jint startPos, jint length,
        jbyteArray compressedData, jboolean isEof) {
    BrotliEncoderState *state = reinterpret_cast<BrotliEncoderState *>(encoderInstance);
    if (!state) return -1;

    jbyte *dataBuf = env->GetByteArrayElements(data, nullptr);
    jbyte *compBuf = env->GetByteArrayElements(compressedData, nullptr);
    jsize compLen = env->GetArrayLength(compressedData);

    const uint8_t *nextIn = reinterpret_cast<const uint8_t *>(dataBuf + startPos);
    size_t availableIn = static_cast<size_t>(length);
    uint8_t *nextOut = reinterpret_cast<uint8_t *>(compBuf);
    size_t availableOut = static_cast<size_t>(compLen);

    BROTLI_BOOL result = BrotliEncoderCompressStream(
            state, isEof ? BROTLI_OPERATION_FINISH : BROTLI_OPERATION_PROCESS,
            &availableIn, &nextIn, &availableOut, &nextOut, nullptr);

    env->ReleaseByteArrayElements(data, dataBuf, JNI_ABORT);
    env->ReleaseByteArrayElements(compressedData, compBuf, 0);

    if (!result) return -1;
    return static_cast<jint>(compLen - availableOut);
}

/**
 * 压缩文件
 */
static jboolean nativeCompressFile(JNIEnv *env, jclass clazz, jlong encoderInstance,
        jstring inputFilePath, jstring outputFilePath) {
    BrotliEncoderState *state = reinterpret_cast<BrotliEncoderState *>(encoderInstance);
    if (!state) return JNI_FALSE;

    const char *inPath = env->GetStringUTFChars(inputFilePath, nullptr);
    const char *outPath = env->GetStringUTFChars(outputFilePath, nullptr);

    FILE *inFile = fopen(inPath, "rb");
    FILE *outFile = fopen(outPath, "wb");
    jboolean ret = JNI_FALSE;

    if (inFile && outFile) {
        size_t availableIn = 0;
        const uint8_t *nextIn = nullptr;
        uint8_t outBuf[64 * 1024];
        bool success = true;

        do {
            // 需要更多输入
            if (availableIn == 0) {
                static uint8_t inBuf[64 * 1024];
                availableIn = fread(inBuf, 1, sizeof(inBuf), inFile);
                nextIn = inBuf;
            }

            size_t availableOut = sizeof(outBuf);
            uint8_t *nextOut = outBuf;
            BROTLI_BOOL isLast = (availableIn == 0 || feof(inFile)) ? BROTLI_TRUE : BROTLI_FALSE;

            if (!BrotliEncoderCompressStream(state,
                    isLast ? BROTLI_OPERATION_FINISH : BROTLI_OPERATION_PROCESS,
                    &availableIn, &nextIn, &availableOut, &nextOut, nullptr)) {
                success = false;
                break;
            }

            size_t outSize = sizeof(outBuf) - availableOut;
            if (outSize > 0) {
                fwrite(outBuf, 1, outSize, outFile);
            }

            if (isLast && BrotliEncoderIsFinished(state)) break;
        } while (!feof(inFile) || availableIn > 0);

        if (success) ret = JNI_TRUE;
    }

    if (inFile) fclose(inFile);
    if (outFile) fclose(outFile);
    env->ReleaseStringUTFChars(inputFilePath, inPath);
    env->ReleaseStringUTFChars(outputFilePath, outPath);

    return ret;
}

/**
 * 销毁压缩器实例
 */
static void nativeDestroyBrotliCompressorInstance(JNIEnv *env, jclass clazz,
        jlong encoderInstance) {
    BrotliEncoderState *state = reinterpret_cast<BrotliEncoderState *>(encoderInstance);
    if (state) {
        BrotliEncoderDestroyInstance(state);
    }
}

// ===================== DeCompressor JNI =====================

/**
 * 创建解压器实例
 */
static jlong nativeCreateBrotliDeCompressorInstance(JNIEnv *env, jclass clazz) {
    BrotliDecoderState *state = BrotliDecoderCreateInstance(nullptr, nullptr, nullptr);
    return reinterpret_cast<jlong>(state);
}

/**
 * 解压数据块
 * 输出通过回调 writeCompressedData 写入 Java 端
 * 返回 0 表示成功，-1 表示出错
 */
static jint nativeDeCompress(JNIEnv *env, jobject thiz, jlong decoderInstance,
        jbyteArray data, jint startPos, jint length) {
    BrotliDecoderState *state = reinterpret_cast<BrotliDecoderState *>(decoderInstance);
    if (!state) return -1;

    jbyte *dataBuf = env->GetByteArrayElements(data, nullptr);
    const uint8_t *nextIn = reinterpret_cast<const uint8_t *>(dataBuf + startPos);
    size_t availableIn = static_cast<size_t>(length);

    // 获取 writeCompressedData 方法，用于回调 Java 端
    jclass clazz = env->GetObjectClass(thiz);
    jmethodID writeMethod = env->GetMethodID(clazz, "writeCompressedData", "([BII)V");

    jint ret = 0;
    while (availableIn > 0) {
        jbyteArray outArr = env->NewByteArray(DECOMPRESS_OUTPUT_BUFFER_SIZE);
        jbyte *outBuf = env->GetByteArrayElements(outArr, nullptr);
        uint8_t *nextOut = reinterpret_cast<uint8_t *>(outBuf);
        size_t availableOut = DECOMPRESS_OUTPUT_BUFFER_SIZE;

        BrotliDecoderResult result = BrotliDecoderDecompressStream(
                state, &availableIn, &nextIn, &availableOut, &nextOut, nullptr);

        env->ReleaseByteArrayElements(outArr, outBuf, 0);

        size_t outSize = DECOMPRESS_OUTPUT_BUFFER_SIZE - availableOut;
        if (outSize > 0) {
            env->CallVoidMethod(thiz, writeMethod, outArr, 0, (jint)outSize);
        }
        env->DeleteLocalRef(outArr);

        if (result == BROTLI_DECODER_RESULT_ERROR) {
            LOGE("Decompress error: %s",
                 BrotliDecoderErrorString(BrotliDecoderGetErrorCode(state)));
            ret = -1;
            break;
        }
    }

    env->ReleaseByteArrayElements(data, dataBuf, JNI_ABORT);
    return ret;
}

/**
 * 解压文件
 */
static jboolean nativeDeCompressFile(JNIEnv *env, jobject thiz, jlong decoderInstance,
        jstring inputFilePath, jstring outputFilePath) {
    BrotliDecoderState *state = reinterpret_cast<BrotliDecoderState *>(decoderInstance);
    if (!state) return JNI_FALSE;

    const char *inPath = env->GetStringUTFChars(inputFilePath, nullptr);
    const char *outPath = env->GetStringUTFChars(outputFilePath, nullptr);

    FILE *inFile = fopen(inPath, "rb");
    FILE *outFile = fopen(outPath, "wb");
    jboolean ret = JNI_FALSE;

    if (inFile && outFile) {
        size_t availableIn = 0;
        const uint8_t *nextIn = nullptr;
        bool success = true;

        do {
            if (availableIn == 0) {
                static uint8_t inBuf[64 * 1024];
                availableIn = fread(inBuf, 1, sizeof(inBuf), inFile);
                nextIn = inBuf;
            }

            uint8_t outBuf[64 * 1024];
            uint8_t *nextOut = outBuf;
            size_t availableOut = sizeof(outBuf);

            BrotliDecoderResult result = BrotliDecoderDecompressStream(
                    state, &availableIn, &nextIn, &availableOut, &nextOut, nullptr);

            size_t outSize = sizeof(outBuf) - availableOut;
            if (outSize > 0) {
                fwrite(outBuf, 1, outSize, outFile);
            }

            if (result == BROTLI_DECODER_RESULT_ERROR) {
                success = false;
                break;
            }
        } while (!feof(inFile) || availableIn > 0);

        if (success) ret = JNI_TRUE;
    }

    if (inFile) fclose(inFile);
    if (outFile) fclose(outFile);
    env->ReleaseStringUTFChars(inputFilePath, inPath);
    env->ReleaseStringUTFChars(outputFilePath, outPath);

    return ret;
}

/**
 * 销毁解压器实例
 */
static void nativeDestroyBrotliDeCompressorInstance(JNIEnv *env, jobject thiz,
        jlong decoderInstance) {
    BrotliDecoderState *state = reinterpret_cast<BrotliDecoderState *>(decoderInstance);
    if (state) {
        BrotliDecoderDestroyInstance(state);
    }
}

// ===================== JNI 方法注册表 =====================

// BrotliCompressor 的 native 方法（静态方法）
static JNINativeMethod gCompressorMethods[] = {
    {"nativeCreateBrotliCompressorInstance", "(IIII)J", (void *)nativeCreateBrotliCompressorInstance},
    {"nativeCompress", "(J[BII[BZ)I", (void *)nativeCompress},
    {"nativeCompressFile", "(JLjava/lang/String;Ljava/lang/String;)Z", (void *)nativeCompressFile},
    {"nativeDestroyBrotliCompressorInstance", "(J)V", (void *)nativeDestroyBrotliCompressorInstance},
};

// BrotliDeCompressor 的 native 方法（实例方法）
static JNINativeMethod gDecompressorMethods[] = {
    {"nativeCreateBrotliDeCompressorInstance", "()J", (void *)nativeCreateBrotliDeCompressorInstance},
    {"nativeDeCompress", "(J[BII)I", (void *)nativeDeCompress},
    {"nativeDeCompressFile", "(JLjava/lang/String;Ljava/lang/String;)Z", (void *)nativeDeCompressFile},
    {"nativeDestroyBrotliDeCompressorInstance", "(J)V", (void *)nativeDestroyBrotliDeCompressorInstance},
};

// ===================== JNI_OnLoad =====================

/**
 * 动态注册所有 native 方法
 */
JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env = nullptr;
    if (vm->GetEnv((void **)&env, JNI_VERSION_1_6) != JNI_OK) {
        return -1;
    }

    // 注册 BrotliCompressor 的 native 方法
    jclass compressorClass = env->FindClass("com/netease/hearttouch/brotlij/BrotliCompressor");
    if (compressorClass) {
        env->RegisterNatives(compressorClass, gCompressorMethods,
                sizeof(gCompressorMethods) / sizeof(JNINativeMethod));
        env->DeleteLocalRef(compressorClass);
    } else {
        LOGE("Failed to find BrotliCompressor class");
        return -1;
    }

    // 注册 BrotliDeCompressor 的 native 方法
    jclass decompressorClass = env->FindClass("com/netease/hearttouch/brotlij/BrotliDeCompressor");
    if (decompressorClass) {
        env->RegisterNatives(decompressorClass, gDecompressorMethods,
                sizeof(gDecompressorMethods) / sizeof(JNINativeMethod));
        env->DeleteLocalRef(decompressorClass);
    } else {
        LOGE("Failed to find BrotliDeCompressor class");
        return -1;
    }

    return JNI_VERSION_1_6;
}