@echo off
"E:\\android_sdk\\cmake\\3.22.1\\bin\\cmake.exe" ^
  "-HE:\\EchoContext\\echo\\pinyin-engine\\src\\main\\cpp" ^
  "-DCMAKE_SYSTEM_NAME=Android" ^
  "-DCMAKE_EXPORT_COMPILE_COMMANDS=ON" ^
  "-DCMAKE_SYSTEM_VERSION=24" ^
  "-DANDROID_PLATFORM=android-24" ^
  "-DANDROID_ABI=x86_64" ^
  "-DCMAKE_ANDROID_ARCH_ABI=x86_64" ^
  "-DANDROID_NDK=E:\\android_sdk\\ndk\\28.0.13004108" ^
  "-DCMAKE_ANDROID_NDK=E:\\android_sdk\\ndk\\28.0.13004108" ^
  "-DCMAKE_TOOLCHAIN_FILE=E:\\android_sdk\\ndk\\28.0.13004108\\build\\cmake\\android.toolchain.cmake" ^
  "-DCMAKE_MAKE_PROGRAM=E:\\android_sdk\\cmake\\3.22.1\\bin\\ninja.exe" ^
  "-DCMAKE_LIBRARY_OUTPUT_DIRECTORY=E:\\EchoContext\\echo\\pinyin-engine\\build\\intermediates\\cxx\\Debug\\6wa53155\\obj\\x86_64" ^
  "-DCMAKE_RUNTIME_OUTPUT_DIRECTORY=E:\\EchoContext\\echo\\pinyin-engine\\build\\intermediates\\cxx\\Debug\\6wa53155\\obj\\x86_64" ^
  "-DCMAKE_BUILD_TYPE=Debug" ^
  "-BE:\\EchoContext\\echo\\pinyin-engine\\.cxx\\Debug\\6wa53155\\x86_64" ^
  -GNinja
