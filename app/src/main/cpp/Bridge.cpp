#include <jni.h>

#include <string>
#include <vector>

#include "Emulator.h"

#define BRIDGE(name) Java_dev_neofodere_agba_core_NativeCore_##name

namespace {

std::string toString(JNIEnv* env, jstring value) {
    if (!value) {
        return std::string();
    }
    const char* chars = env->GetStringUTFChars(value, nullptr);
    std::string result(chars ? chars : "");
    if (chars) {
        env->ReleaseStringUTFChars(value, chars);
    }
    return result;
}

}  // namespace

extern "C" {

JNIEXPORT void JNICALL BRIDGE(setOption)(JNIEnv* env, jobject, jstring key, jstring value) {
    Emulator::instance().setOption(toString(env, key), toString(env, value));
}

JNIEXPORT jint JNICALL BRIDGE(load)(JNIEnv* env, jobject, jint romFd, jint patchFd, jstring savePath) {
    return Emulator::instance().load(romFd, patchFd, toString(env, savePath));
}

JNIEXPORT void JNICALL BRIDGE(start)(JNIEnv*, jobject) {
    Emulator::instance().start();
}

JNIEXPORT void JNICALL BRIDGE(shutdown)(JNIEnv*, jobject) {
    Emulator::instance().shutdown();
}

JNIEXPORT jint JNICALL BRIDGE(platform)(JNIEnv*, jobject) {
    return Emulator::instance().platform();
}

JNIEXPORT void JNICALL BRIDGE(setPaused)(JNIEnv*, jobject, jboolean paused) {
    Emulator::instance().setPaused(paused == JNI_TRUE);
}

JNIEXPORT jboolean JNICALL BRIDGE(isPaused)(JNIEnv*, jobject) {
    return Emulator::instance().isPaused() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL BRIDGE(reset)(JNIEnv*, jobject) {
    Emulator::instance().reset();
}

JNIEXPORT void JNICALL BRIDGE(setKeys)(JNIEnv*, jobject, jint keys) {
    Emulator::instance().setKeys(static_cast<uint32_t>(keys));
}

JNIEXPORT void JNICALL BRIDGE(setFastForward)(JNIEnv*, jobject, jboolean enabled) {
    Emulator::instance().setFastForward(enabled == JNI_TRUE);
}

JNIEXPORT void JNICALL BRIDGE(setRewinding)(JNIEnv*, jobject, jboolean enabled) {
    Emulator::instance().setRewinding(enabled == JNI_TRUE);
}

JNIEXPORT void JNICALL BRIDGE(setSensors)(JNIEnv*, jobject, jfloat accelX, jfloat accelY, jfloat gyroZ) {
    Emulator::instance().setSensors(accelX, accelY, gyroZ);
}

JNIEXPORT void JNICALL BRIDGE(setLuxIndex)(JNIEnv*, jobject, jint index) {
    Emulator::instance().setLuxIndex(index);
}

JNIEXPORT void JNICALL BRIDGE(setLuxSensor)(JNIEnv*, jobject, jfloat lux, jboolean useSensor) {
    Emulator::instance().setLuxSensor(lux, useSensor == JNI_TRUE);
}

JNIEXPORT jfloat JNICALL BRIDGE(rumbleLevel)(JNIEnv*, jobject) {
    return Emulator::instance().rumbleLevel();
}

JNIEXPORT jboolean JNICALL BRIDGE(saveState)(JNIEnv* env, jobject, jstring path) {
    return Emulator::instance().saveState(toString(env, path)) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL BRIDGE(loadState)(JNIEnv* env, jobject, jstring path) {
    return Emulator::instance().loadState(toString(env, path)) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL BRIDGE(setCheats)(JNIEnv* env, jobject, jobjectArray names, jobjectArray codes,
                                         jbooleanArray enabled) {
    std::vector<CheatEntry> entries;
    jsize count = env->GetArrayLength(names);
    jboolean* flags = env->GetBooleanArrayElements(enabled, nullptr);
    for (jsize i = 0; i < count; ++i) {
        auto name = static_cast<jstring>(env->GetObjectArrayElement(names, i));
        auto code = static_cast<jstring>(env->GetObjectArrayElement(codes, i));
        entries.push_back({toString(env, name), toString(env, code), flags[i] == JNI_TRUE});
        env->DeleteLocalRef(name);
        env->DeleteLocalRef(code);
    }
    env->ReleaseBooleanArrayElements(enabled, flags, JNI_ABORT);
    Emulator::instance().applyCheats(entries);
}

JNIEXPORT void JNICALL BRIDGE(setGbPalette)(JNIEnv* env, jobject, jstring name) {
    Emulator& emulator = Emulator::instance();
    std::string value = toString(env, name);
    emulator.setOption("gbPalette", value);
    emulator.applyGbPalette(value);
}

JNIEXPORT jint JNICALL BRIDGE(readFrame)(JNIEnv* env, jobject, jobject buffer, jboolean force) {
    auto* address = static_cast<uint8_t*>(env->GetDirectBufferAddress(buffer));
    if (!address) {
        return 0;
    }
    return Emulator::instance().readFrame(address, static_cast<size_t>(env->GetDirectBufferCapacity(buffer)),
                                         force == JNI_TRUE);
}

JNIEXPORT jobjectArray JNICALL BRIDGE(gbPalettes)(JNIEnv* env, jobject) {
    std::vector<std::string> names = Emulator::gbPaletteNames();
    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray result = env->NewObjectArray(static_cast<jsize>(names.size()), stringClass, nullptr);
    for (size_t i = 0; i < names.size(); ++i) {
        jstring name = env->NewStringUTF(names[i].c_str());
        env->SetObjectArrayElement(result, static_cast<jsize>(i), name);
        env->DeleteLocalRef(name);
    }
    return result;
}

}  // extern "C"
