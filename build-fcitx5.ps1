param(
    [string]$Abi = 'arm64-v8a',
    [switch]$Release,
    [switch]$Plugins,
    [switch]$Check,
    [switch]$Install
)
$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    $upstream = Join-Path $PSScriptRoot 'third_party/fcitx5-android'
    if (!(Test-Path "$upstream/app/build.gradle.kts")) {
        throw 'Missing vendored source: restore third_party/fcitx5-android from the Echo repository'
    }
    # Compatibility patches are already included in the vendored source.
    $bundledJdk = Get-ChildItem "$PSScriptRoot/.tools/jdk21" -Directory -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($bundledJdk) { $env:JAVA_HOME = $bundledJdk.FullName }
    if (Test-Path "$PSScriptRoot/.tools/gettext/bin/msgfmt.exe") {
        $env:PATH = "$PSScriptRoot/.tools/gettext/bin;$env:PATH"
    }
    if (!$env:ECM_DIR -and (Test-Path "$PSScriptRoot/.tools/ecm/share/ECM/cmake")) {
        $env:ECM_DIR = "$PSScriptRoot/.tools/ecm/share/ECM/cmake"
    }
    if (Test-Path local.properties) { Copy-Item local.properties "$upstream/local.properties" -Force }
    $bundledCmake = "$PSScriptRoot/.tools/cmake/cmake-3.31.6-windows-x86_64"
    if (Test-Path "$bundledCmake/bin/cmake.exe") {
        Add-Content "$upstream/local.properties" "`ncmake.dir=$($bundledCmake.Replace('\', '/'))"
    }
    $variant = if ($Release) { 'Release' } else { 'Debug' }
    if ($Release) {
        if (!$env:SIGN_KEY_FILE -or !(Test-Path -LiteralPath $env:SIGN_KEY_FILE) -or
            !$env:SIGN_KEY_ALIAS -or !$env:SIGN_KEY_PWD) {
            throw 'Release requires SIGN_KEY_FILE, SIGN_KEY_ALIAS and SIGN_KEY_PWD for a signed APK'
        }
    }
    $tasks = @(":app:assemble${variant}")
    if ($Plugins) { $tasks += "assemble${variant}Plugins" }
    if ($Check) { $tasks += @(":app:test${variant}UnitTest", ":app:lint${variant}") }
    $gradleArgs = $tasks + @('-I', "$PSScriptRoot/migration/repositories.init.gradle", "-PbuildABI=$Abi", '-PbuildTools=36.0.0', '-PcmakeVersion=3.31.6', '--console=plain')
    $gradle = './gradlew.bat'
    if (Test-Path "$PSScriptRoot/.tools/gradle/gradle-9.6.1/bin/gradle.bat") {
        $gradle = "$PSScriptRoot/.tools/gradle/gradle-9.6.1/bin/gradle.bat"
    }
    & $gradle @gradleArgs
    if ($LASTEXITCODE -ne 0) { throw "Gradle failed ($LASTEXITCODE)" }
    if ($Install) {
        $apk = Get-ChildItem "$upstream/app/build/outputs/apk/$($variant.ToLowerInvariant())" -Filter "*${Abi}*.apk" | Select-Object -First 1
        if (!$apk) { throw "No APK found for $Abi" }
        & adb install -r $apk.FullName
        if ($LASTEXITCODE -ne 0) { throw 'APK installation failed' }
        if ($Plugins) {
            $pluginApks = Get-ChildItem "$upstream/plugin/*/build/outputs/apk/$($variant.ToLowerInvariant())/*.apk" |
                Where-Object { $_.Name -like "*${Abi}*" -or $_.DirectoryName -like '*clipboard-filter*' }
            foreach ($pluginApk in $pluginApks) {
                & adb install -r $pluginApk.FullName
                if ($LASTEXITCODE -ne 0) { throw "Plugin installation failed: $($pluginApk.Name)" }
            }
        }
        $appId = if ($Release) { 'org.fcitx.fcitx5.android' } else { 'org.fcitx.fcitx5.android.debug' }
        & adb shell am start -n "$appId/org.fcitx.fcitx5.android.ui.main.MainActivity"
        if ($LASTEXITCODE -ne 0) { throw 'Could not launch Echo' }
    }
} finally { Pop-Location }
