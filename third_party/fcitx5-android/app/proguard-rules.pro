# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep JNI interface
-keep class org.fcitx.fcitx5.android.core.* { *; }
-keep class org.fcitx.fcitx5.android.data.pinyin.customphrase.PinyinCustomPhrase {
    public <init>(...);
    # JNI reads these fields by name in jni-utils.h (ECHO-002).
    java.lang.String key;
    int order;
    java.lang.String value;
}

# Keep dependency magic
-keep class ** extends org.mechdancer.dependency.Component {
    int hashCode();
    boolean equals(java.lang.Object);
}

# remove kotlin null checks
-processkotlinnullchecks remove

# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Vosk uses JNA to resolve native symbols by Java method names.
-keep class org.vosk.** { *; }
-keep class com.sun.jna.** { *; }
