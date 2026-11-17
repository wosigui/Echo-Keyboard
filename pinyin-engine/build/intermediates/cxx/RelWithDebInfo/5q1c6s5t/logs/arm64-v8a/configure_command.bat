@echo off
"E:\\android_sdk\\cmake\\3.22.1\\bin\\cmake.exe" ^
  "-HE:\\EchoContext\\echo\\pinyin-engine\\src\\main\\cpp" ^
  "-DCMAKE_SYSTEM_NAME=Android" ^
  "-DCMAKE_EXPORT_COMPILE_COMMANDS=ON" ^
  "-DCMAKE_SYSTEM_VERSION=24" ^
  "-DANDROID_PLATFORM=android-24" ^
  "-DANDROID_ABI=arm64-v8a" ^
  "-DCMAKE_ANDROID_ARCH_ABI=arm64-v8a" ^
  "-DANDROID_NDK=E:\\android_sdk\\ndk\\28.0.13004108" ^
  "-DCMAKE_ANDROID_NDK=E:\\android_sdk\\ndk\\28.0.13004108" ^
  "-DCMAKE_TOOLCHAIN_FILE=E:\\android_sdk\\ndk\\28.0.13004108\\build\\cmake\\android.toolchain.cmake" ^
  "-DCMAKE_MAKE_PROGRAM=E:\\android_sdk\\cmake\\3.22.1\\bin\\ninja.exe" ^
  "-DCMAKE_LIBRARY_OUTPUT_DIRECTORY=E:\\EchoContext\\echo\\pinyin-engine\\build\\intermediates\\cxx\\RelWithDebInfo\\5q1c6s5t\\obj\\arm64-v8a" ^
  "-DCMAKE_RUNTIME_OUTPUT_DIRECTORY=E:\\EchoContext\\echo\\pinyin-engine\\build\\intermediates\\cxx\\RelWithDebInfo\\5q1c6s5t\\obj\\arm64-v8a" ^
  "-DCMAKE_BUILD_TYPE=RelWithDebInfo" ^
  "-BE:\\EchoContext\\echo\\pinyin-engine\\.cxx\\RelWithDebInfo\\5q1c6s5t\\arm64-v8a" ^
  -GNinja
