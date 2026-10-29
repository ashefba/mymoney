$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "Find-Browser.ps1")

$browser = Find-AppBrowser
if (-not $browser) {
    throw "MyMoney opens in Microsoft Edge or Google Chrome, but neither was found on this PC. Install one of them, then run the installer again."
}

$installDir = Join-Path $env:LOCALAPPDATA "MyMoney"
$sourceDir = $PSScriptRoot

if ((Resolve-Path $sourceDir).Path -ne $installDir) {
    New-Item -ItemType Directory -Force -Path $installDir | Out-Null
    Get-ChildItem -Path $sourceDir -File | ForEach-Object {
        Copy-Item $_.FullName -Destination $installDir -Force
    }
    if (Test-Path (Join-Path $sourceDir "assets")) {
        Copy-Item (Join-Path $sourceDir "assets") -Destination $installDir -Recurse -Force
    }
}

$indexUri = [System.Uri]::new((Join-Path $installDir "index.html")).AbsoluteUri
$iconPath = Join-Path $installDir "assets\icon.ico"
$iconLocation = if (Test-Path -LiteralPath $iconPath) { "$iconPath,0" } else { "$browser,0" }
$shell = New-Object -ComObject WScript.Shell
$desktop = [Environment]::GetFolderPath("Desktop")
$startMenu = Join-Path $env:APPDATA "Microsoft\Windows\Start Menu\Programs"

foreach ($shortcutPath in @((Join-Path $desktop "MyMoney.lnk"), (Join-Path $startMenu "MyMoney.lnk"))) {
    $shortcut = $shell.CreateShortcut($shortcutPath)
    $shortcut.TargetPath = $browser
    $shortcut.Arguments = "--app=$indexUri --start-maximized"
    $shortcut.WorkingDirectory = $installDir
    $shortcut.IconLocation = $iconLocation
    $shortcut.Description = "MyMoney offline personal finance"
    $shortcut.Save()
}

Add-Type -AssemblyName PresentationFramework
[System.Windows.MessageBox]::Show(
    "MyMoney was installed for this Windows account. Use the new Desktop or Start Menu shortcut to open it.",
    "MyMoney installed",
    "OK",
    "Information"
) | Out-Null

Start-Process -FilePath $browser -ArgumentList @("--app=$indexUri", "--start-maximized")
