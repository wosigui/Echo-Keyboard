# Echo release APK

主应用 release 开启 R8 代码压缩、名称混淆和资源压缩；JNI 接口保留现有 keep 规则。

## 签名与构建

在 PowerShell 中设置 `SIGN_KEY_FILE`（keystore 绝对路径）、`SIGN_KEY_ALIAS`、`SIGN_KEY_PWD`，然后执行：

```powershell
./build-fcitx5.ps1 -Release
```

默认生成 arm64-v8a APK。需要其他架构时指定 `-Abi`；需要插件时加 `-Plugins`，插件应与主应用使用相同 release 签名。`-Check` 执行对应变体的单元测试和 Lint；`-Install` 安装并打开应用。

首次分享构建的签名存放于仓库外的 `E:\EchoContext\echo-release-signing\`，其中 `echo-release.jks` 为密钥，`signing.json` 为本机签名配置（包含密码）。这两个文件需私下备份，不要提交或分享给安装者。后续更新必须复用同一签名，并在发布新版时提高 versionCode。

本机复用签名：

```powershell
$releaseSigning = Get-Content 'E:\EchoContext\echo-release-signing\signing.json' -Raw | ConvertFrom-Json
$env:SIGN_KEY_FILE = $releaseSigning.storeFile
$env:SIGN_KEY_ALIAS = $releaseSigning.alias
$env:SIGN_KEY_PWD = $releaseSigning.password
./build-fcitx5.ps1 -Release
```

原始 APK：`third_party/fcitx5-android/app/build/outputs/apk/release/`。

每次发布应单独归档 APK、SHA-256 和 `app/build/outputs/mapping/release/mapping.txt`；mapping 用于还原该版本的混淆堆栈。

## 人工验收

安装 release 后启用并切换到 Echo，检查中文输入和候选、英文和符号、剪贴板、主题与设置持久化、手机重启后的输入。使用插件时另测插件发现、加载和输入。Debug 与 release 包名不同，需要分别启用输入法。

## 首次构建记录（2026-11-18）

`./build-fcitx5.ps1 -Release` 构建成功，`lintVitalRelease` 通过。APK 已整理为 `build/release/Echo-arm64-v8a-release.apk`，54,472,559 字节；包名 `org.fcitx.fcitx5.android`，versionCode 112，最低 Android 6.0，无 debuggable 标志。apksigner 验证 v1/v2 签名通过，证书为 Echo Release。R8 mapping 已确认应用类名缩短，归档于 `build/release/mapping.txt`，最终 R8 配置无 `-dontobfuscate`。

APK SHA-256：`eff3aa37bc62e04e7f9ce7ba68b926c6ee13d551c231c97fdc812c68386db4a6`。

本次只生成主应用，未构建 release 插件；ADB 无连接设备，未进行安装和真机输入验收。
