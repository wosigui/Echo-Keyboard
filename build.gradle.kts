plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
}
ext {
    set("HOST_PKG", providers.gradleProperty("releasePkgName").getOrElse("app.echo.ime"))
    set("COMPILE_SDK", 36)
    set("MIN_SDK", 24)
    set("TARGET_SDK", 36)
    set("VERSION_CODE", providers.gradleProperty("versionCode").getOrElse("1").toInt())
    set("VERSION_NAME", providers.gradleProperty("versionName").getOrElse("1.0.0"))
}
