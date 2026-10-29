MYMONEY DESKTOP FOR WINDOWS
===========================

Requirements
------------
- Windows 10 or Windows 11
- Microsoft Edge (included with Windows) or Google Chrome

Install
-------
1. Extract the whole MyMoney-Windows folder from the ZIP. Do not run it inside
   the ZIP viewer.
2. Double-click "Install MyMoney.cmd".
3. MyMoney is copied to:
      %LOCALAPPDATA%\MyMoney
4. Shortcuts are added to your Desktop and Start Menu.

Windows may ask whether PowerShell is allowed to run the local installer.
The installer is plain text and does not need administrator access.

Portable use
------------
To run without installing, extract the folder and double-click:
  "Launch MyMoney.cmd"

Data and privacy
----------------
- MyMoney makes no network requests. It has no account, ads or tracking.
- Data is stored on this PC by the browser that opens MyMoney. Edge is used
  when it is installed; otherwise Google Chrome.
- Create encrypted backups regularly under Settings.
- Store backups outside the MyMoney installation folder.
- Clearing all browsing data in that browser can erase MyMoney's data.
- The web version of MyMoney (opened from a link) keeps its own separate data.
  To move data between them, use Settings > Encrypted backup, then
  Settings > Restore backup in the other copy.

Features
--------
Dashboard and safe-to-spend, transaction search and CSV export, accounts and
transfers, overall and category budgets, savings goals and contributions,
bills and payment recording, debts and principal/interest repayments,
12-month planning forecasts, weekly/monthly/yearly reports, print-to-PDF,
light/dark themes, JSON export, AES-GCM encrypted backup and restore.

Money is stored and calculated as integer cents, never floating-point currency.

Uninstall
---------
1. Back up your data in MyMoney first.
2. Delete the MyMoney shortcuts from your Desktop and Start Menu.
3. Delete %LOCALAPPDATA%\MyMoney.
4. In the browser MyMoney used, clear stored data for local files if you also
   want to permanently remove the local financial database.

Important limitation
--------------------
This package opens MyMoney in an Edge (or Chrome) app window to avoid bundling
a second browser engine. It is a portable Windows desktop application, not a
code-signed .exe/.msi installer. Windows packaging tools were unavailable in
the build environment.
