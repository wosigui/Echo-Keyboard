# AOSP Pinyin decoder

Source mirror: https://github.com/oxangen/PinyinIME (master archive, retrieved 2026-11-17).
Original: https://android.googlesource.com/platform/packages/inputmethods/PinyinIME/
License: Apache-2.0; full notice bundled as assets/AOSP-Pinyin-NOTICE.txt.
Vendored runtime files: pinyin-engine/src/main/cpp/pinyin; dictionary: pinyin-engine/src/main/assets/dict_pinyin.dat.
Echo changes: replace cutils logging include with public Android logging header;
expose read-only MatrixSearch::echo_score() for T9 beam ranking.
Echo JNI/T9 adapter is in pinyin-engine/src/main/cpp/echo_jni.cpp.
No upstream app UI or branding is included in the APK.
Source archive SHA-256: ffa3289caf1559ed831a5a7a087ea17c5a317142134d6fdc9c46eae1413820b2
