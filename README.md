# Echo · Fcitx5 Android

Echo 默认工程现以 Fcitx5 Android 为基础，复用完整 Android 应用、原生引擎、设置、候选、主题、词库管理、剪贴板和插件工程。

上游：https://github.com/fcitx5-android/fcitx5-android
固定版本：eb5b50216e70484a009933d9519db76005f1faf3。源码与递归依赖作为普通文件由 Echo 仓库直接管理；Echo 品牌配置位于 migration/echo.gradle，Android 资源兼容与主题迁移修正已合入源码；输入引擎和插件生产逻辑复用上游。

## 构建

```powershell
./build-fcitx5.ps1 -Check
# 同时构建所有插件 APK
./build-fcitx5.ps1 -Plugins
# 安装主 APK 并打开设置页
./build-fcitx5.ps1 -Install
```

需要 JDK 21、SDK 36、Build Tools 36.0.0、NDK 28.0.13004108、CMake 3.31.6、GNU gettext（msgfmt 在 PATH 中）及 extra-cmake-modules（ECM_DIR 指向安装后的 share/ECM/cmake）。Gradle 使用上游 9.6.1。根 local.properties 配置 SDK，脚本同步到上游目录。

默认构建 arm64-v8a，其他架构可传 -Abi x86_64。Android Studio 打开根目录，默认 :app、:lib:* 和 :plugin:* 都指向上游。不要从 third_party 目录单独构建，否则不会加载 Echo 品牌配置。

主 APK：third_party/fcitx5-android/app/build/outputs/apk/debug/。
插件 APK：third_party/fcitx5-android/plugin/<插件名>/build/outputs/apk/debug/。

为完整复用插件协议，保留上游 namespace/applicationId。debug 主包为 org.fcitx.fcitx5.android.debug，显示名为 Echo。旧 app.echo.ime 可以并存，不自动迁移旧词库和偏好。插件须与主应用使用相同构建类型和签名构建安装，不承诺第三方发布 APK 的签名兼容。

## 功能边界

- 主应用内置拼音、双拼、五笔等，具体输入法需要在设置添加和启用。
- RIME、粤拼、注音、日语、韩语等是独立插件，不在主 APK 内。RIME 自定义方案仍需导入。
- 原来自写的九宫格、搜狗风格界面尚未移植，不列为新主应用已完成功能。
- 已移除不参与构建的旧 app/、pinyin-engine/、PinyinIME 源码及旧迁移配置，当前应用源码位于 third_party/fcitx5-android/app/。

## 验收与许可证

检查命令构建主应用并运行 testDebugUnitTest、lintDebug；插件使用 -Plugins 另外构建。人工测试见 docs/fcitx5-migration.md。上游已有的 266 项缺失翻译逐项记录于 migration/lint-baseline.xml，新问题仍会报错；这不代表所有上游语言已翻译完整。

Fcitx5 Android 使用 LGPL-2.1-or-later，许可证及原作者声明保留于 third_party/fcitx5-android/ 及各依赖源码目录。原生库和插件使用各自许可证；发布需按实际依赖履行相应义务。根原有 Apache 许可证不替代第三方许可证。

## 离线语音输入

默认长按空格键打开 Vosk 语音输入，也可在键盘设置中选择其他长按行为；已有显式设置会保留。首次授权后再次长按，选择普通话或英语，确认下载约 40–42 MB 的模型。后续离线识别，录音不上传。点击“输入”结束录音并提交，取消或离开输入框会丢弃结果，最长录音 60 秒。密码框不启用语音。

引擎采用 Apache-2.0 的 [Vosk](https://github.com/alphacep/vosk-api)，模型采用官方 Apache-2.0 的 vosk-model-small-cn-0.22 / vosk-model-small-en-us-0.15，详见[模型列表](https://alphacephei.com/vosk/models)。小模型适合移动端，但中文准确率和标点能力有限。

人工验收：首次拒绝/允许麦克风；模型下载失败后重试；中英文短句及多次停顿；结束后只输入一次；取消、返回、切换输入框不误提交；下载完成后断网复用；短按空格及其他长按设置正常；密码框不录音。
