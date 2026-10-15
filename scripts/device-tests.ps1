param(
    [Parameter(Mandatory=$true)][string]$SdkRoot,
    [Parameter(Mandatory=$true)][string]$ApkDirectory,
    [Parameter(Mandatory=$true)][string]$Serial,
    [string]$ApplicationId = 'com.dk.appblocker.audit',
    [string]$ResultsDirectory = 'dist/device-results',
    [switch]$AllowEmulatorReboot,
    [switch]$VerifyPackagesOnly
)
$ErrorActionPreference = 'Stop'
$adb = Join-Path $SdkRoot 'platform-tools/adb.exe'
function DeviceCommand {
    param([Parameter(ValueFromRemainingArguments=$true)][string[]]$DeviceArguments)
    $result = & $adb -s $Serial @DeviceArguments 2>&1 | Out-String
    if ($LASTEXITCODE -ne 0) { throw $result }
    return $result
}
New-Item -ItemType Directory -Force $ResultsDirectory | Out-Null
$product = Get-ChildItem -LiteralPath $ApkDirectory -Recurse -Filter app-debug.apk | Select-Object -First 1
$testApk = Get-ChildItem -LiteralPath $ApkDirectory -Recurse -Filter app-debug-androidTest.apk | Select-Object -First 1
if (!$product -or !$testApk) { throw 'Expected debug and instrumentation APKs were not found.' }
$aapt = Join-Path $SdkRoot 'build-tools/35.0.0/aapt.exe'
function Apk-Package([string]$Path) {
    $metadata = & $aapt dump badging $Path | Out-String
    if ($LASTEXITCODE -ne 0 -or $metadata -notmatch "package: name='([^']+)'") { throw "Cannot inspect APK package: $Path" }
    return $Matches[1]
}
if ((Apk-Package $product.FullName) -cne $ApplicationId -or (Apk-Package $testApk.FullName) -cne "$ApplicationId.test") {
    throw 'APK package IDs do not match the requested isolated audit target. Nothing has been installed.'
}
if ($VerifyPackagesOnly) { Write-Output "Verified isolated APK package IDs: $ApplicationId and $ApplicationId.test; no device changes made."; return }
$emulator = (DeviceCommand shell getprop ro.kernel.qemu).Trim() -eq '1'
if (!$emulator -and $ApplicationId -ne 'com.dk.appblocker.audit') {
    throw 'Physical-device testing requires the isolated audit package so user app data is preserved.'
}
if ($AllowEmulatorReboot -and !$emulator) { throw 'This runner never reboots a physical phone.' }
$runner = "$ApplicationId.test/androidx.test.runner.AndroidJUnitRunner"
function Run-Instrumentation {
    param([string]$Name, [string[]]$ExtraArguments)
    $report = DeviceCommand shell am instrument -w -r @ExtraArguments $runner
    $report | Set-Content (Join-Path $ResultsDirectory "$Name.txt")
    if ($report -notmatch 'OK \(\d+ tests?\)' -or $report -match 'FAILURES!!!|INSTRUMENTATION_FAILED|shortMsg=') { throw "Device tests failed: $Name" }
    Write-Output "$Name passed"
}
try {
    DeviceCommand install -r $product.FullName | Set-Content (Join-Path $ResultsDirectory 'install.txt')
    DeviceCommand install -r -t $testApk.FullName | Out-Null
    Run-Instrumentation 'instrumentation' @('-e','notClass','com.dk.appblocker.PersistenceDeviceTest,com.dk.appblocker.SaveDurabilityTest')
    foreach ($phase in @('rules','appearance')) {
        $report = DeviceCommand shell am instrument -w -r -e class com.dk.appblocker.SaveDurabilityTest -e phase $phase $runner
        $report | Set-Content (Join-Path $ResultsDirectory "immediate-kill-$phase.txt")
        if ($report -notmatch 'Process crashed') { throw "Expected immediate process termination after saving $phase" }
    }
    Run-Instrumentation 'immediate-kill-verify' @('-e','class','com.dk.appblocker.SaveDurabilityTest','-e','phase','verify')
    foreach ($phase in @('prepare','process')) {
        Run-Instrumentation "persistence-$phase" @('-e','class','com.dk.appblocker.PersistenceDeviceTest','-e','phase',$phase)
    }
    if ($AllowEmulatorReboot) {
        DeviceCommand reboot | Out-Null
        Start-Sleep -Seconds 2
        DeviceCommand wait-for-device | Out-Null
        $until = (Get-Date).AddMinutes(4)
        while ((DeviceCommand shell getprop sys.boot_completed).Trim() -ne '1') {
            if ((Get-Date) -gt $until) { throw 'Emulator reboot timed out' }
            Start-Sleep -Seconds 2
        }
        DeviceCommand shell input keyevent 82 | Out-Null
        Run-Instrumentation 'persistence-reboot' @('-e','class','com.dk.appblocker.PersistenceDeviceTest','-e','phase','reboot')
    }
    DeviceCommand pull "/sdcard/Android/data/$ApplicationId/files" (Join-Path $ResultsDirectory 'screenshots-and-profile') | Out-Null
} finally {
    DeviceCommand logcat -d | Set-Content (Join-Path $ResultsDirectory 'logcat.txt')
    DeviceCommand shell dumpsys accessibility | Set-Content (Join-Path $ResultsDirectory 'accessibility.txt')
}
