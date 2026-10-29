# Shared by Launch-MyMoney.ps1 and Install-MyMoney.ps1.
# Edge is preferred because it ships with Windows 10 and 11. Keep the order stable:
# each browser keeps its own copy of MyMoney's data.

function Get-RegisteredApp([string]$ExeName) {
    foreach ($hive in "HKCU:", "HKLM:") {
        $entry = Get-ItemProperty -LiteralPath "$hive\SOFTWARE\Microsoft\Windows\CurrentVersion\App Paths\$ExeName" -ErrorAction SilentlyContinue
        if ($entry -and $entry.'(default)') { $entry.'(default)'.Trim('"') }
    }
}

function Find-AppBrowser {
    $candidates = @(
        "${env:ProgramFiles(x86)}\Microsoft\Edge\Application\msedge.exe",
        "$env:ProgramFiles\Microsoft\Edge\Application\msedge.exe",
        "$env:LOCALAPPDATA\Microsoft\Edge\Application\msedge.exe"
    )
    $candidates += @(Get-RegisteredApp "msedge.exe")
    $candidates += @(
        "$env:ProgramFiles\Google\Chrome\Application\chrome.exe",
        "${env:ProgramFiles(x86)}\Google\Chrome\Application\chrome.exe",
        "$env:LOCALAPPDATA\Google\Chrome\Application\chrome.exe"
    )
    $candidates += @(Get-RegisteredApp "chrome.exe")
    $candidates | Where-Object { $_ -and (Test-Path -LiteralPath $_ -PathType Leaf) } | Select-Object -First 1
}
