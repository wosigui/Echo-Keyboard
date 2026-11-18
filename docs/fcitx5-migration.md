# Fcitx5 全面复用验收

根 settings.gradle.kts 保留标准工程根目录，将各子模块的 projectDir 映射到上游目录，并复用上游 build-logic。根 wrapper 和版本目录与上游同步。品牌层通过 Android Components finalizeDsl 统一 app_name 为 Echo，并补齐生成资源到 Lint 任务的依赖。资源补丁修正引导图标 tint 和日语数字占位符；测试补丁核对主题 2.0 到 2.1 的迁移及三个候选文字颜色。输入引擎和插件逻辑直接复用上游。

固定源码版本：eb5b50216e70484a009933d9519db76005f1faf3。升级时同步根 wrapper、版本目录和 settings，并重新验收。

## 人工测试

1. 打开 Echo，完成系统输入法启用和切换，确认选中的是新包服务。
2. 在设置添加拼音、双拼、五笔，分别测试输入与切换。
3. 输入 nihao、beijingdaxue 和长句，检查候选、展开、逐词选择、删除、空格和回车。
4. 测试英文、数字、符号、Emoji、长按、剪贴板和文本编辑。
5. 调整键盘高度、反馈和主题；检查生效和重启持久化。
6. 安装与主包同类型、同签名构建的 RIME 插件，检查发现、添加、方案导入和输入；其他插件逐个重复。
7. 验证密码框、旋转屏幕、多应用切换和手机重启后的初始化。
8. 确认旧 Echo 包与数据仍在；旧数据尚未迁移。

## 验证记录

2026-11-18：主应用及全部 9 个插件 arm64-v8a debug 构建成功；5 个单元测试全部通过。lintDebug 为 0 errors、49 warnings；266 个上游既有缺失翻译按具体问题记录为基线，未关闭 MissingTranslation 检查。8 份字符串 XML 解析通过。10 个 APK 的签名校验通过，证书一致。主包应用标签在各语言下均为 Echo，包名和插件协议保持上游规则。

本轮安装包整理在 build/fcitx5/，主包为 echo-fcitx5-arm64-debug.apk，9 个插件位于 plugins/，checksums.json 记录 SHA-256。

设备在构建期间从 ADB 断开，最后 adb devices 为空；尚未安装打开新包，也未进行真机输入、插件加载或重启测试。设备重新连接后执行 ./build-fcitx5.ps1 -Plugins -Install，再按上述步骤验收。
## ECHO-001：Studio 提示 Module not specified

内部问题编号：ECHO-001。旧运行配置绑定 EchoKeyboard.app，同时迁移曾修改 rootProject.projectDir，导致 Studio 同步后没有正确导入应用模块。

修复：保留工作区为 Gradle 根目录，逐个映射上游模块；新增 .run/Echo.run.xml，绑定 EchoFcitx5.app。原生预编译依赖通过 :lib:fcitx5 的实际目录定位，修正已合入当前源码。IDE 编译工具位置由项目配置传递，不再要求通过 PowerShell 脚本注入环境变量。

验证：使用 Studio 自带 D:/Android_kaola/jbr，清除 ECM_DIR 后直接运行 Gradle :app:assembleDebug，构建成功。原生工具定位和模块映射已验证；Studio 需执行 File > Sync Project with Gradle Files，再选择 Echo 运行配置刷新当前会话。

## 工程清理

2026-11-18：移除未参与当前 Gradle 构建的根 app/、pinyin-engine/、third_party/PinyinIME-master/、旧构建配置、已合入源码的补丁副本及旧 UI 验收截图与报告。根构建产物、日志、上游 IDE 配置和 ECM 安装后的构建中间目录一并移出工作区。当前源码与依赖仍位于 third_party/fcitx5-android/，保留许可证、Echo 配置、Lint 基线和构建必需工具。
