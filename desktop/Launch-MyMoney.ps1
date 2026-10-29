$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "Find-Browser.ps1")

$browser = Find-AppBrowser
if (-not $browser) {
    Add-Type -AssemblyName PresentationFramework
    [System.Windows.MessageBox]::Show(
        "MyMoney opens in Microsoft Edge or Google Chrome, but neither was found on this PC. Install one of them, then try again.",
        "MyMoney",
        "OK",
        "Error"
    ) | Out-Null
    exit 1
}

$indexPath = (Resolve-Path (Join-Path $PSScriptRoot "index.html")).Path
$appUri = [System.Uri]::new($indexPath).AbsoluteUri
Start-Process -FilePath $browser -ArgumentList @("--app=$appUri", "--start-maximized")
