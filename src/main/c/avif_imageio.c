/**
 * avif-imageio JNI implementation
 * 
 * This file implements the JNI bridge between Java and libavif.
 */

#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include <avif/avif.h>
#include "avif_imageio.h"

/* ============================================================================
 * Helper structures
 * ============================================================================ */

typedef struct {
    int quality;      // 0-100
    int speed;        // 0-10
    int bitDepth;     // 8, 10, 12
    int lossless;     // 0 or 1
} EncoderConfig;

typedef struct {
    int ignoreIcc;    // 0 or 1
    int ignoreExif;   // 0 or 1
} DecoderOptions;

/* ============================================================================
 * Helper functions
 * ============================================================================ */

static void throwIOException(JNIEnv *env, const char* message) {
    jclass excClass = (*env)->FindClass(env, "java/io/IOException");
    if (excClass != NULL) {
        (*env)->ThrowNew(env, excClass, message);
    }
}

static void throwIllegalArgumentException(JNIEnv *env, const char* message) {
    jclass excClass = (*env)->FindClass(env, "java/lang/IllegalArgumentException");
    if (excClass != NULL) {
        (*env)->ThrowNew(env, excClass, message);
    }
}

static jfieldID getPointerField(JNIEnv *env, jobject obj) {
    jclass cls = (*env)->GetObjectClass(env, obj);
    return (*env)->GetFieldID(env, cls, "fPointer", "J");
}

static jlong getPointer(JNIEnv *env, jobject obj) {
    jfieldID fid = getPointerField(env, obj);
    return (*env)->GetLongField(env, obj, fid);
}

/* ============================================================================
 * Encoder Options JNI methods
 * ============================================================================ */

JNIEXPORT jlong JNICALL Java_com_github_avifimageio_AvifEncoderOptions_createConfig
  (JNIEnv *env, jclass cls) {
    EncoderConfig *config = (EncoderConfig*)malloc(sizeof(EncoderConfig));
    if (config == NULL) {
        return 0;
    }
    config->quality = 60;   // DEFAULT_QUALITY
    config->speed = 6;      // DEFAULT_SPEED
    config->bitDepth = 8;   // DEFAULT_BIT_DEPTH
    config->lossless = 0;
    return (jlong)(intptr_t)config;
}

JNIEXPORT void JNICALL Java_com_github_avifimageio_AvifEncoderOptions_deleteConfig
  (JNIEnv *env, jclass cls, jlong ptr) {
    if (ptr != 0) {
        free((void*)(intptr_t)ptr);
    }
}

JNIEXPORT jint JNICALL Java_com_github_avifimageio_AvifEncoderOptions_getQuality
  (JNIEnv *env, jobject obj) {
    EncoderConfig *config = (EncoderConfig*)(intptr_t)getPointer(env, obj);
    return config ? config->quality : 60;
}

JNIEXPORT void JNICALL Java_com_github_avifimageio_AvifEncoderOptions_setQuality
  (JNIEnv *env, jobject obj, jint quality) {
    if (quality < 0 || quality > 100) {
        throwIllegalArgumentException(env, "Quality must be between 0 and 100");
        return;
    }
    EncoderConfig *config = (EncoderConfig*)(intptr_t)getPointer(env, obj);
    if (config) {
        config->quality = quality;
    }
}

JNIEXPORT jint JNICALL Java_com_github_avifimageio_AvifEncoderOptions_getSpeed
  (JNIEnv *env, jobject obj) {
    EncoderConfig *config = (EncoderConfig*)(intptr_t)getPointer(env, obj);
    return config ? config->speed : 6;
}

JNIEXPORT void JNICALL Java_com_github_avifimageio_AvifEncoderOptions_setSpeed
  (JNIEnv *env, jobject obj, jint speed) {
    if (speed < 0 || speed > 10) {
        throwIllegalArgumentException(env, "Speed must be between 0 and 10");
        return;
    }
    EncoderConfig *config = (EncoderConfig*)(intptr_t)getPointer(env, obj);
    if (config) {
        config->speed = speed;
    }
}

JNIEXPORT jint JNICALL Java_com_github_avifimageio_AvifEncoderOptions_getBitDepth
  (JNIEnv *env, jobject obj) {
    EncoderConfig *config = (EncoderConfig*)(intptr_t)getPointer(env, obj);
    return config ? config->bitDepth : 8;
}

JNIEXPORT void JNICALL Java_com_github_avifimageio_AvifEncoderOptions_setBitDepth
  (JNIEnv *env, jobject obj, jint bitDepth) {
    if (bitDepth != 8 && bitDepth != 10 && bitDepth != 12) {
        throwIllegalArgumentException(env, "Bit depth must be 8, 10, or 12");
        return;
    }
    EncoderConfig *config = (EncoderConfig*)(intptr_t)getPointer(env, obj);
    if (config) {
        config->bitDepth = bitDepth;
    }
}

JNIEXPORT jboolean JNICALL Java_com_github_avifimageio_AvifEncoderOptions_isLossless
  (JNIEnv *env, jobject obj) {
    EncoderConfig *config = (EncoderConfig*)(intptr_t)getPointer(env, obj);
    return config ? (config->lossless != 0) : JNI_FALSE;
}

JNIEXPORT void JNICALL Java_com_github_avifimageio_AvifEncoderOptions_setLossless
  (JNIEnv *env, jobject obj, jboolean lossless) {
    EncoderConfig *config = (EncoderConfig*)(intptr_t)getPointer(env, obj);
    if (config) {
        config->lossless = lossless ? 1 : 0;
    }
}

/* ============================================================================
 * Decoder Options JNI methods
 * ============================================================================ */

JNIEXPORT jlong JNICALL Java_com_github_avifimageio_AvifDecoderOptions_createOptions
  (JNIEnv *env, jclass cls) {
    DecoderOptions *options = (DecoderOptions*)malloc(sizeof(DecoderOptions));
    if (options == NULL) {
        return 0;
    }
    options->ignoreIcc = 0;
    options->ignoreExif = 0;
    return (jlong)(intptr_t)options;
}

JNIEXPORT void JNICALL Java_com_github_avifimageio_AvifDecoderOptions_deleteOptions
  (JNIEnv *env, jclass cls, jlong ptr) {
    if (ptr != 0) {
        free((void*)(intptr_t)ptr);
    }
}

JNIEXPORT jboolean JNICALL Java_com_github_avifimageio_AvifDecoderOptions_isIgnoreIcc
  (JNIEnv *env, jobject obj) {
    DecoderOptions *options = (DecoderOptions*)(intptr_t)getPointer(env, obj);
    return options ? (options->ignoreIcc != 0) : JNI_FALSE;
}

JNIEXPORT void JNICALL Java_com_github_avifimageio_AvifDecoderOptions_setIgnoreIcc
  (JNIEnv *env, jobject obj, jboolean ignore) {
    DecoderOptions *options = (DecoderOptions*)(intptr_t)getPointer(env, obj);
    if (options) {
        options->ignoreIcc = ignore ? 1 : 0;
    }
}

JNIEXPORT jboolean JNICALL Java_com_github_avifimageio_AvifDecoderOptions_isIgnoreExif
  (JNIEnv *env, jobject obj) {
    DecoderOptions *options = (DecoderOptions*)(intptr_t)getPointer(env, obj);
    return options ? (options->ignoreExif != 0) : JNI_FALSE;
}

JNIEXPORT void JNICALL Java_com_github_avifimageio_AvifDecoderOptions_setIgnoreExif
  (JNIEnv *env, jobject obj, jboolean ignore) {
    DecoderOptions *options = (DecoderOptions*)(intptr_t)getPointer(env, obj);
    if (options) {
        options->ignoreExif = ignore ? 1 : 0;
    }
}

/* ============================================================================
 * Avif main class JNI methods
 * ============================================================================ */

JNIEXPORT jobject JNICALL Java_com_github_avifimageio_Avif_getInfoNative
  (JNIEnv *env, jclass cls, jbyteArray data, jint offset, jint length) {
    
    jbyte *dataBytes = (*env)->GetByteArrayElements(env, data, NULL);
    if (dataBytes == NULL) {
        throwIOException(env, "Failed to get byte array elements");
        return NULL;
    }
    
    avifDecoder *decoder = avifDecoderCreate();
    if (decoder == NULL) {
        (*env)->ReleaseByteArrayElements(env, data, dataBytes, JNI_ABORT);
        throwIOException(env, "Failed to create AVIF decoder");
        return NULL;
    }
    
    avifResult result = avifDecoderSetIOMemory(decoder, 
        (const uint8_t*)(dataBytes + offset), (size_t)length);
    if (result != AVIF_RESULT_OK) {
        avifDecoderDestroy(decoder);
        (*env)->ReleaseByteArrayElements(env, data, dataBytes, JNI_ABORT);
        throwIOException(env, avifResultToString(result));
        return NULL;
    }
    
    result = avifDecoderParse(decoder);
    if (result != AVIF_RESULT_OK) {
        avifDecoderDestroy(decoder);
        (*env)->ReleaseByteArrayElements(env, data, dataBytes, JNI_ABORT);
        throwIOException(env, avifResultToString(result));
        return NULL;
    }
    
    // Create ImageInfo object
    jclass imageInfoClass = (*env)->FindClass(env, "com/github/avifimageio/ImageInfo");
    if (imageInfoClass == NULL) {
        avifDecoderDestroy(decoder);
        (*env)->ReleaseByteArrayElements(env, data, dataBytes, JNI_ABORT);
        return NULL;
    }
    
    jmethodID constructor = (*env)->GetMethodID(env, imageInfoClass, "<init>", 
        "(IIIZIDZZ)V");
    if (constructor == NULL) {
        avifDecoderDestroy(decoder);
        (*env)->ReleaseByteArrayElements(env, data, dataBytes, JNI_ABORT);
        return NULL;
    }
    
    int width = decoder->image->width;
    int height = decoder->image->height;
    int bitDepth = decoder->image->depth;
    int hasAlpha = decoder->alphaPresent ? 1 : 0;
    int frameCount = decoder->imageCount;
    double duration = decoder->duration;
    int hasIccProfile = (decoder->image->icc.size > 0) ? 1 : 0;
    int hasExif = (decoder->image->exif.size > 0) ? 1 : 0;
    
    jobject imageInfo = (*env)->NewObject(env, imageInfoClass, constructor,
        width, height, bitDepth, (jboolean)hasAlpha,
        frameCount, duration, (jboolean)hasIccProfile, (jboolean)hasExif);
    
    avifDecoderDestroy(decoder);
    (*env)->ReleaseByteArrayElements(env, data, dataBytes, JNI_ABORT);
    
    return imageInfo;
}

static jobject decodeInternal(JNIEnv *env, jlong optionsPtr, jbyteArray data, 
                              jint offset, jint length, jint frameIndex) {
    
    jbyte *dataBytes = (*env)->GetByteArrayElements(env, data, NULL);
    if (dataBytes == NULL) {
        throwIOException(env, "Failed to get byte array elements");
        return NULL;
    }
    
    avifDecoder *decoder = avifDecoderCreate();
    if (decoder == NULL) {
        (*env)->ReleaseByteArrayElements(env, data, dataBytes, JNI_ABORT);
        throwIOException(env, "Failed to create AVIF decoder");
        return NULL;
    }
    
    avifResult result = avifDecoderSetIOMemory(decoder, 
        (const uint8_t*)(dataBytes + offset), (size_t)length);
    if (result != AVIF_RESULT_OK) {
        avifDecoderDestroy(decoder);
        (*env)->ReleaseByteArrayElements(env, data, dataBytes, JNI_ABORT);
        throwIOException(env, avifResultToString(result));
        return NULL;
    }
    
    result = avifDecoderParse(decoder);
    if (result != AVIF_RESULT_OK) {
        avifDecoderDestroy(decoder);
        (*env)->ReleaseByteArrayElements(env, data, dataBytes, JNI_ABORT);
        throwIOException(env, avifResultToString(result));
        return NULL;
    }
    
    // Decode specific frame or first frame
    if (frameIndex >= 0) {
        result = avifDecoderNthImage(decoder, frameIndex);
    } else {
        result = avifDecoderNextImage(decoder);
    }
    
    if (result != AVIF_RESULT_OK) {
        avifDecoderDestroy(decoder);
        (*env)->ReleaseByteArrayElements(env, data, dataBytes, JNI_ABORT);
        throwIOException(env, avifResultToString(result));
        return NULL;
    }
    
    avifImage *image = decoder->image;
    int width = image->width;
    int height = image->height;
    int bitDepth = image->depth;
    int hasAlpha = decoder->alphaPresent ? 1 : 0;
    
    // Convert to RGB
    avifRGBImage rgb;
    avifRGBImageSetDefaults(&rgb, image);
    rgb.format = hasAlpha ? AVIF_RGB_FORMAT_RGBA : AVIF_RGB_FORMAT_RGB;
    rgb.depth = 8;
    
    avifRGBImageAllocatePixels(&rgb);
    
    result = avifImageYUVToRGB(image, &rgb);
    if (result != AVIF_RESULT_OK) {
        avifRGBImageFreePixels(&rgb);
        avifDecoderDestroy(decoder);
        (*env)->ReleaseByteArrayElements(env, data, dataBytes, JNI_ABORT);
        throwIOException(env, avifResultToString(result));
        return NULL;
    }
    
    // Convert to ARGB int array for Java
    int pixelCount = width * height;
    jintArray pixelsArray = (*env)->NewIntArray(env, pixelCount);
    if (pixelsArray == NULL) {
        avifRGBImageFreePixels(&rgb);
        avifDecoderDestroy(decoder);
        (*env)->ReleaseByteArrayElements(env, data, dataBytes, JNI_ABORT);
        return NULL;
    }
    
    jint *pixels = (*env)->GetIntArrayElements(env, pixelsArray, NULL);
    if (pixels == NULL) {
        avifRGBImageFreePixels(&rgb);
        avifDecoderDestroy(decoder);
        (*env)->ReleaseByteArrayElements(env, data, dataBytes, JNI_ABORT);
        return NULL;
    }
    
    uint8_t *rgbPixels = rgb.pixels;
    int bytesPerPixel = hasAlpha ? 4 : 3;
    
    for (int i = 0; i < pixelCount; i++) {
        int r = rgbPixels[i * bytesPerPixel];
        int g = rgbPixels[i * bytesPerPixel + 1];
        int b = rgbPixels[i * bytesPerPixel + 2];
        int a = hasAlpha ? rgbPixels[i * bytesPerPixel + 3] : 255;
        pixels[i] = (a << 24) | (r << 16) | (g << 8) | b;
    }
    
    (*env)->ReleaseIntArrayElements(env, pixelsArray, pixels, 0);
    
    // Get ICC profile if present
    jbyteArray iccArray = NULL;
    if (image->icc.size > 0) {
        iccArray = (*env)->NewByteArray(env, (jsize)image->icc.size);
        if (iccArray != NULL) {
            (*env)->SetByteArrayRegion(env, iccArray, 0, (jsize)image->icc.size, 
                (const jbyte*)image->icc.data);
        }
    }
    
    avifRGBImageFreePixels(&rgb);
    avifDecoderDestroy(decoder);
    (*env)->ReleaseByteArrayElements(env, data, dataBytes, JNI_ABORT);
    
    // Create DecodeResult object
    jclass decodeResultClass = (*env)->FindClass(env, "com/github/avifimageio/DecodeResult");
    if (decodeResultClass == NULL) {
        return NULL;
    }
    
    jmethodID constructor = (*env)->GetMethodID(env, decodeResultClass, "<init>", 
        "([IIIZI[B)V");
    if (constructor == NULL) {
        return NULL;
    }
    
    return (*env)->NewObject(env, decodeResultClass, constructor,
        pixelsArray, width, height, (jboolean)hasAlpha, bitDepth, iccArray);
}

JNIEXPORT jobject JNICALL Java_com_github_avifimageio_Avif_decodeNative
  (JNIEnv *env, jclass cls, jlong optionsPtr, jbyteArray data, jint offset, jint length) {
    return decodeInternal(env, optionsPtr, data, offset, length, -1);
}

JNIEXPORT jobject JNICALL Java_com_github_avifimageio_Avif_decodeFrameNative
  (JNIEnv *env, jclass cls, jlong optionsPtr, jbyteArray data, jint offset, jint length, jint frameIndex) {
    return decodeInternal(env, optionsPtr, data, offset, length, frameIndex);
}

static jbyteArray encodeInternal(JNIEnv *env, jlong configPtr, jbyteArray pixelData,
                                  jint width, jint height, jint stride, int hasAlpha) {
    
    EncoderConfig *config = (EncoderConfig*)(intptr_t)configPtr;
    int quality = config ? config->quality : 60;
    int speed = config ? config->speed : 6;
    int bitDepth = config ? config->bitDepth : 8;
    int lossless = config ? config->lossless : 0;
    
    jbyte *pixels = (*env)->GetByteArrayElements(env, pixelData, NULL);
    if (pixels == NULL) {
        throwIOException(env, "Failed to get byte array elements");
        return NULL;
    }
    
    avifImage *image = avifImageCreate(width, height, bitDepth, AVIF_PIXEL_FORMAT_YUV444);
    if (image == NULL) {
        (*env)->ReleaseByteArrayElements(env, pixelData, pixels, JNI_ABORT);
        throwIOException(env, "Failed to create AVIF image");
        return NULL;
    }
    
    // Set up RGB image
    avifRGBImage rgb;
    avifRGBImageSetDefaults(&rgb, image);
    rgb.format = hasAlpha ? AVIF_RGB_FORMAT_RGBA : AVIF_RGB_FORMAT_RGB;
    rgb.depth = 8;
    rgb.pixels = (uint8_t*)pixels;
    rgb.rowBytes = stride;
    
    // Convert RGB to YUV
    avifResult result = avifImageRGBToYUV(image, &rgb);
    if (result != AVIF_RESULT_OK) {
        avifImageDestroy(image);
        (*env)->ReleaseByteArrayElements(env, pixelData, pixels, JNI_ABORT);
        throwIOException(env, avifResultToString(result));
        return NULL;
    }
    
    // Create encoder
    avifEncoder *encoder = avifEncoderCreate();
    if (encoder == NULL) {
        avifImageDestroy(image);
        (*env)->ReleaseByteArrayElements(env, pixelData, pixels, JNI_ABORT);
        throwIOException(env, "Failed to create AVIF encoder");
        return NULL;
    }
    
    // Configure encoder
    encoder->speed = speed;
    
    if (lossless) {
        encoder->quality = AVIF_QUALITY_LOSSLESS;
        encoder->qualityAlpha = AVIF_QUALITY_LOSSLESS;
    } else {
        // quality 范围: 0-100, 直接使用
        encoder->quality = quality;
        encoder->qualityAlpha = quality;
    }
    
    // Encode
    avifRWData output = AVIF_DATA_EMPTY;
    result = avifEncoderAddImage(encoder, image, 1, AVIF_ADD_IMAGE_FLAG_SINGLE);
    if (result != AVIF_RESULT_OK) {
        avifEncoderDestroy(encoder);
        avifImageDestroy(image);
        (*env)->ReleaseByteArrayElements(env, pixelData, pixels, JNI_ABORT);
        throwIOException(env, avifResultToString(result));
        return NULL;
    }
    
    result = avifEncoderFinish(encoder, &output);
    if (result != AVIF_RESULT_OK) {
        avifEncoderDestroy(encoder);
        avifImageDestroy(image);
        (*env)->ReleaseByteArrayElements(env, pixelData, pixels, JNI_ABORT);
        throwIOException(env, avifResultToString(result));
        return NULL;
    }
    
    // Create result byte array
    jbyteArray resultArray = (*env)->NewByteArray(env, (jsize)output.size);
    if (resultArray != NULL) {
        (*env)->SetByteArrayRegion(env, resultArray, 0, (jsize)output.size, 
            (const jbyte*)output.data);
    }
    
    avifRWDataFree(&output);
    avifEncoderDestroy(encoder);
    avifImageDestroy(image);
    (*env)->ReleaseByteArrayElements(env, pixelData, pixels, JNI_ABORT);
    
    return resultArray;
}

JNIEXPORT jbyteArray JNICALL Java_com_github_avifimageio_Avif_encodeRGBNative
  (JNIEnv *env, jclass cls, jlong configPtr, jbyteArray rgbData, jint width, jint height, jint stride) {
    return encodeInternal(env, configPtr, rgbData, width, height, stride, 0);
}

JNIEXPORT jbyteArray JNICALL Java_com_github_avifimageio_Avif_encodeRGBANative
  (JNIEnv *env, jclass cls, jlong configPtr, jbyteArray rgbaData, jint width, jint height, jint stride) {
    return encodeInternal(env, configPtr, rgbaData, width, height, stride, 1);
}

JNIEXPORT jbyteArray JNICALL Java_com_github_avifimageio_Avif_getExifNative
  (JNIEnv *env, jclass cls, jbyteArray data, jint offset, jint length) {
    
    jbyte *dataBytes = (*env)->GetByteArrayElements(env, data, NULL);
    if (dataBytes == NULL) {
        throwIOException(env, "Failed to get byte array elements");
        return NULL;
    }
    
    avifDecoder *decoder = avifDecoderCreate();
    if (decoder == NULL) {
        (*env)->ReleaseByteArrayElements(env, data, dataBytes, JNI_ABORT);
        throwIOException(env, "Failed to create AVIF decoder");
        return NULL;
    }
    
    avifResult result = avifDecoderSetIOMemory(decoder, 
        (const uint8_t*)(dataBytes + offset), (size_t)length);
    if (result != AVIF_RESULT_OK) {
        avifDecoderDestroy(decoder);
        (*env)->ReleaseByteArrayElements(env, data, dataBytes, JNI_ABORT);
        throwIOException(env, avifResultToString(result));
        return NULL;
    }
    
    result = avifDecoderParse(decoder);
    if (result != AVIF_RESULT_OK) {
        avifDecoderDestroy(decoder);
        (*env)->ReleaseByteArrayElements(env, data, dataBytes, JNI_ABORT);
        throwIOException(env, avifResultToString(result));
        return NULL;
    }
    
    jbyteArray exifArray = NULL;
    if (decoder->image->exif.size > 0) {
        exifArray = (*env)->NewByteArray(env, (jsize)decoder->image->exif.size);
        if (exifArray != NULL) {
            (*env)->SetByteArrayRegion(env, exifArray, 0, (jsize)decoder->image->exif.size, 
                (const jbyte*)decoder->image->exif.data);
        }
    }
    
    avifDecoderDestroy(decoder);
    (*env)->ReleaseByteArrayElements(env, data, dataBytes, JNI_ABORT);
    
    return exifArray;
}

JNIEXPORT jbyteArray JNICALL Java_com_github_avifimageio_Avif_getIccProfileNative
  (JNIEnv *env, jclass cls, jbyteArray data, jint offset, jint length) {
    
    jbyte *dataBytes = (*env)->GetByteArrayElements(env, data, NULL);
    if (dataBytes == NULL) {
        throwIOException(env, "Failed to get byte array elements");
        return NULL;
    }
    
    avifDecoder *decoder = avifDecoderCreate();
    if (decoder == NULL) {
        (*env)->ReleaseByteArrayElements(env, data, dataBytes, JNI_ABORT);
        throwIOException(env, "Failed to create AVIF decoder");
        return NULL;
    }
    
    avifResult result = avifDecoderSetIOMemory(decoder, 
        (const uint8_t*)(dataBytes + offset), (size_t)length);
    if (result != AVIF_RESULT_OK) {
        avifDecoderDestroy(decoder);
        (*env)->ReleaseByteArrayElements(env, data, dataBytes, JNI_ABORT);
        throwIOException(env, avifResultToString(result));
        return NULL;
    }
    
    result = avifDecoderParse(decoder);
    if (result != AVIF_RESULT_OK) {
        avifDecoderDestroy(decoder);
        (*env)->ReleaseByteArrayElements(env, data, dataBytes, JNI_ABORT);
        throwIOException(env, avifResultToString(result));
        return NULL;
    }
    
    jbyteArray iccArray = NULL;
    if (decoder->image->icc.size > 0) {
        iccArray = (*env)->NewByteArray(env, (jsize)decoder->image->icc.size);
        if (iccArray != NULL) {
            (*env)->SetByteArrayRegion(env, iccArray, 0, (jsize)decoder->image->icc.size, 
                (const jbyte*)decoder->image->icc.data);
        }
    }
    
    avifDecoderDestroy(decoder);
    (*env)->ReleaseByteArrayElements(env, data, dataBytes, JNI_ABORT);
    
    return iccArray;
}
