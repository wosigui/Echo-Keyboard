pluginManagement {
    includeBuild("third_party/fcitx5-android/build-logic")
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "EchoFcitx5"
val echoCustomization = file("migration/echo.gradle")
gradle.beforeProject { apply(from = echoCustomization) }

include(":lib:common")
include(":lib:fcitx5")
include(":lib:fcitx5-lua")
include(":lib:libime")
include(":lib:fcitx5-chinese-addons")
include(":codegen")
include(":app")
include(":lib:plugin-base")
include(":plugin:anthy")
include(":plugin:clipboard-filter")
include(":plugin:unikey")
include(":plugin:rime")
include(":plugin:hangul")
include(":plugin:chewing")
include(":plugin:sayura")
include(":plugin:jyutping")
include(":plugin:thai")

// Keep the Gradle root at the directory opened by Android Studio.
// Relocating rootProject.projectDir makes IDE model import lose its modules.
fun mapUpstreamModules(parent: ProjectDescriptor) {
    parent.children.forEach { module ->
        module.projectDir = file("third_party/fcitx5-android/" + module.path.trimStart(':').replace(':', '/'))
        mapUpstreamModules(module)
    }
}
mapUpstreamModules(rootProject)
