@echo off
setlocal
if not exist "%~dp0Install-MyMoney.ps1" goto :not_extracted
if not exist "%~dp0Find-Browser.ps1" goto :not_extracted
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0Install-MyMoney.ps1"
if errorlevel 1 (
  echo.
  echo Installation failed. See the message above.
  pause
)
exit /b

:not_extracted
echo Extract the whole MyMoney-Windows folder from the ZIP first,
echo then run "Install MyMoney.cmd" from the extracted folder.
pause
exit /b 1
