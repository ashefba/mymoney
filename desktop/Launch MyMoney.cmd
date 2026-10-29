@echo off
setlocal
if not exist "%~dp0Launch-MyMoney.ps1" goto :not_extracted
if not exist "%~dp0Find-Browser.ps1" goto :not_extracted
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0Launch-MyMoney.ps1"
if errorlevel 1 pause
exit /b

:not_extracted
echo Extract the whole MyMoney-Windows folder from the ZIP first,
echo then run "Launch MyMoney.cmd" from the extracted folder.
pause
exit /b 1
