# Echo 输入法

可直接开发源码的 Android 离线输入法，Kotlin + XML + C++ / JNI。

## 功能

- 按用户提供的搜狗小米版截图实现深色键盘：六入口工具栏、四排按键、字母大写字形、顶部符号提示、独立符号/数字/中英键、语音空格键及底部输入法/剪贴板入口。图标为代码绘制。
- AOSP PinyinIME 开源拼音解码器与系统词库：全拼、简拼、整句解码、词频排序、上屏后的词语联想。
- 中文九宫格 T9 / 全键切换，记住布局偏好；英文、符号及输入框类型适配。
- 逐字/逐词选择后保留未转换拼音；空格选择首候选；支持 `bjdx → 北京大学`。
- 候选展开与选择、九键拼音组合筛选、分类符号、数字键盘、Emoji/颜文字、文本选择/光标/剪切/复制/粘贴。
- 本机用户词库持久学习已选候选及连续选词，按使用次数和最近使用时间排序；可在工具箱关闭学习或清除词库。输入「今天」联想「天气」，连续选词还会学习个人联想。
- 全键拼音支持相邻字母交换、同排邻键误触、重复字母与常见模糊音纠正；纠错和模糊音可独立开关。完整转换候选优先于部分转换候选。
- 英文前缀补全使用离线词频表，支持大小写匹配和个人常用词优先。
- 剪贴板在键盘可见且输入框允许时读取，最多保留 20 条本机记录，支持粘贴、单条删除和清空；可关闭记录。密码等敏感输入框禁用预览，系统标记敏感的剪贴板不收录。
- Emoji 分类、中英文关键词搜索、最近使用及清空；按住空格横滑移动光标，编辑面板支持文本选择、剪切、复制和粘贴。
- 工具箱支持按键音、触感及按键气泡开关，按键行高 40–68 dp、左手/右手单手模式和恢复全宽；长按字母输入顶部提示符号，长按空格调用系统语音识别。
- 无网络权限。个人词库、剪贴板及 Emoji 最近使用保存在应用本机数据库；密码及禁止个性化学习的输入框不学习。原生引擎内部用户词典使用临时缓存，跨会话学习由应用数据库负责。

九键基于 AOSP 解码代价进行宽度 64 的束搜索，属于 Echo 的适配层，并非直接移植商业输入法引擎。语音委托手机已安装的识别服务，可能由该服务联网处理；未安装时给出提示，未集成搜狗语音服务。暂无云词库、手写、滑行输入、皮肤/表情商店和完整错拼纠正。仅全键布局有用户截图参照，其余面板按同一深色风格实现，不宣称完整复刻搜狗。

## 源码位置

Android Studio 打开项目根目录并 Sync Gradle，即可看到 `app` 和 `pinyin-engine` 两个模块。
`pinyin-engine` 是 Android Library，包含 AOSP C++ 解码器、JNI 接口、词库及许可证。
app 通过源码模块依赖使用引擎：

```kotlin
dependencies {
    implementation(project(":pinyin-engine"))
}
```

模块自动通过 CMake 编译原生库，并把词库和开源声明打包进 app；无需手动复制 so 或词库。
公开接口为 `app.echo.pinyin.NativePinyin` 和 `PinyinCandidate`，JNI 名称保护规则通过 consumer ProGuard 配置传递给 app。
可单独执行 `./gradlew :pinyin-engine:assembleRelease`，生成 `pinyin-engine/build/outputs/aar/pinyin-engine-release.aar`。
引擎为进程内单例，调用方应在同一工作线程串行调用 `open/query/predict/close`；app 已实现该调度。

- `app/src/main/java/app/echo/ime/EchoInputMethodService.kt`：输入状态、候选选择、异步调度。
- `app/src/main/java/app/echo/ime/EchoKeyboardView.kt`：按键绘制，保留系统触摸处理。
- `app/src/main/java/app/echo/ime/KeyboardPanels.kt`：工具箱、布局、符号、表情、编辑、剪贴板与候选面板。
- `app/src/main/java/app/echo/ime/KeyboardIconView.kt`：工具栏和按键图标。
- `app/src/main/java/app/echo/ime/VoiceInputActivity.kt`：系统语音识别入口。
- `pinyin-engine/src/main/cpp/pinyin`：Apache-2.0 AOSP 解码器源码。
- `pinyin-engine/src/main/cpp/echo_jni.cpp`：JNI、T9 搜索、候选与消耗长度。
- `app/src/main/res/xml/{letters,nine_key,numbers}.xml`：键盘布局；分类符号由面板渲染。
- `pinyin-engine/src/main/assets/dict_pinyin.dat`：AOSP 系统词库。
- `third_party/README.md`：来源和修改说明；Apache 声明随 APK 打包。

## 构建

Java 17、Gradle 8.7、AGP 8.5.1、Kotlin 2.2.0、NDK 28.0.13004108、CMake 3.22.1。
支持 Android 7.0+；compile/target SDK 36，四种 ABI，原生库采用 16 KB 页面兼容链接参数。

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug
.\gradlew.bat assembleDebugAndroidTest
# 在测试设备安装主 APK 和测试 APK 后：
adb shell am instrument -w app.echo.ime.test/androidx.test.runner.AndroidJUnitRunner
```

安装 `app/build/outputs/apk/debug/app-debug.apk`，打开 Echo，先在系统设置启用，再切换输入法。
工具栏键盘图标可切换九键、全键、英文和数字；九键输入 `64426` 可选择「你好」，`94664486` 可选择「中国」。九键「分词」可按候选的拼音组合筛选。
候选栏可横向滑动；拼音原文位于候选末尾，可直接上屏。

设备测试覆盖全拼、整句、`bjdx` 简拼、部分候选消耗长度、T9 音节锁定与回退、联想、错拼/模糊音及开关、词库重开持久化与词频排序、英文补全、剪贴板删除/清空、Emoji 搜索/最近使用、反馈设置、单手设置、尺寸与触摸区域，以及键盘弹起时首页测试框的可见性。滑动手感、气泡显示、实际振动/音效及系统语音识别质量仍需真机验收。
release 未配置私有签名，发布前需自行配置。
