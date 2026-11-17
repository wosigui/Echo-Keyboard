plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}
android {
    namespace = "app.echo.ime"
    compileSdk = rootProject.ext["COMPILE_SDK"] as Int
    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        applicationId = rootProject.ext["HOST_PKG"] as String
        minSdk = rootProject.ext["MIN_SDK"] as Int
        targetSdk = rootProject.ext["TARGET_SDK"] as Int
        versionCode = rootProject.ext["VERSION_CODE"] as Int
        versionName = rootProject.ext["VERSION_NAME"] as String
    }
    buildFeatures { viewBinding = true }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation(project(":pinyin-engine"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.lifecycle.viewmodel)
    testImplementation(libs.junit)
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
}
