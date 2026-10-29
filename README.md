# MyMoney — private personal finance

MyMoney tracks your accounts, spending, budgets, savings goals, bills and debts, and tells you how much is **safe to spend each day** until payday. It has no login, no ads and no tracking. Your data stays on your device.

There are two editions:

- **Web app** (`desktop/`): one dependency-free page that installs like an app on Android phones and Windows PCs, works offline, and doubles as the Windows portable package.
- **Native Android app** (`app/`): Kotlin, Jetpack Compose (Material 3), Room, MVVM and DataStore.

Both store amounts as whole cents, never as floating point. EUR is the default currency.

## Web app for your phone and PC

### Run it locally

```bash
python3 -m http.server 43261 --directory desktop
```

Open `http://127.0.0.1:43261`. Add `?demo=1` to explore sample data without saving anything.

### Publish it at a permanent link

`vercel.json` serves `desktop/` as a static site, with no build step, and adds a strict Content Security Policy. The policy blocks every network request except to the app's own address. Deploy the repository root with Vercel (`vercel deploy --prod`, or import the repository in the Vercel dashboard).

`desktop/` also works on any static host. `desktop/_headers` carries the same security headers for Netlify and Cloudflare Pages, so you can drag the `desktop/` folder (or the extracted `MyMoney-web` folder) onto [Netlify Drop](https://app.netlify.com/drop) to get a link without a build. Keep using the same link afterwards: each browser stores your data for that address only.

### Install it

- **Android (Chrome):** open the link and tap **Install** on the dashboard banner, or use the menu (⋮) → **Install app** / **Add to Home screen**.
- **Windows (Edge or Chrome):** open the link and click **Install** on the banner, or use the install icon in the address bar.

Once installed, MyMoney opens in its own window, starts offline, and has an **Add transaction** shortcut on the app icon.

### Where your data lives

Data is saved in the browser storage of the device you use (`localStorage`), and MyMoney asks the browser to keep it persistent. Nothing is uploaded. Each device keeps its own data. To move data between devices, use **Settings → Encrypted backup** and **Restore backup**. Clearing the browser's site data erases MyMoney's data. Once you have 10 or more transactions, the dashboard reminds you to back up if you haven't in the last 30 days.

Receipt photos are resized to at most 1280 px before saving. If storage is ever full, the change is rejected and nothing already saved is lost.

### Windows portable package

The same `desktop/` folder runs straight from disk on Windows. `Install MyMoney.cmd` copies it to `%LOCALAPPDATA%\MyMoney` and adds Desktop and Start Menu shortcuts that open it in an Edge app window (or Chrome when Edge is missing). See `desktop/README-Windows.txt`.

## Native Android app

The rest of this README covers the Kotlin app in `app/`. It has no internet permission, and amounts are calculated with `BigDecimal`.

## Features

| Area | What you get |
| --- | --- |
| **Home** | Total balance, monthly income / expenses / savings, savings rate, remaining budget, daily safe-to-spend, upcoming payments, recent transactions, goal progress, 6-month trend and category charts (tap to inspect), month switcher |
| **Transactions** | Add, edit, delete, search and filter by type, category, account and month. Fields: amount, date, category, account, payment method, description, notes, and an optional receipt photo (camera or gallery, stored privately). Default and custom categories. Quick add from the centre **+** button |
| **Accounts** | Current, savings, cash, credit card and other. Opening balances, automatic balances and transfers. Transfers are never counted as spending |
| **Budgets** | Overall and per-category monthly budgets with progress and overspend warnings. Safe-to-spend until payday or month end, after unpaid bills and planned savings |
| **Savings goals** | Target, already saved, target date, progress, required monthly amount, pace-based completion estimate, contributions and withdrawals (optionally with a real transfer, without double counting) |
| **Bills & subscriptions** | Amount, frequency, next due date, reminder days, calendar view, mark paid / skip. Nothing is deducted automatically. Local notifications via AlarmManager |
| **Debts** | Loans, money borrowed, money lent. Balance, history, repayment split into principal and interest (interest is paid first, simple daily interest), payoff projection chart and the payment needed to hit a target date |
| **Planning** | Balance in 6 and 12 months, time to reach a goal, the effect of spending X % less, and "can I afford this?". Forecast chart with its assumptions stated |
| **Reports** | Weekly / monthly / yearly summaries, category breakdowns with period-over-period change, income vs expenses, savings trend, budget performance. Export to **PDF** and **CSV** on the device |
| **Data protection** | Room database in app-private storage. Optional biometric / screen-lock app lock (also hides the app in Recents). **AES-256-GCM encrypted backup** (PBKDF2-SHA256, 150k iterations) and restore, JSON export of all data, transactions CSV, and permanent delete-all |

Navigation: **Home · Transactions · (+) · Budgets · Savings · More**. More opens Accounts, Bills, Debts, Planning, Reports, Categories and Settings.

### How money is counted

- **Income / expenses** come only from `INCOME` and `EXPENSE` transactions.
- **Transfers** between your own accounts move money without counting as spending or income.
- **Goal contributions** set aside money you already have. They change account balances only if you also record a transfer.
- **Debt principal** (borrowing, lending, repaying) moves money but isn't income or spending. Only **interest** is recorded as an expense, or as income when you lent the money.
- **Safe to spend** = spendable balances (current, cash and credit cards by default) − unpaid bills due before the period ends − planned savings not yet moved to savings accounts. It is capped by your remaining budget when one is set. The daily figure divides that by the days left, rounding down.

## Offline guarantee

- `AndroidManifest.xml` explicitly **removes** `android.permission.INTERNET` (`tools:node="remove"`), so no library can merge it back in.
- No analytics, crash reporting, ads or cloud SDKs are included.
- `allowBackup="false"` and data-extraction rules keep your data out of cloud backups. Backups exist only when you create a file yourself.
- Permissions used: `POST_NOTIFICATIONS` (bill reminders), `RECEIVE_BOOT_COMPLETED` (re-schedule reminders) and `USE_BIOMETRIC` (app lock). Camera capture goes through the system camera app, so no camera permission is needed.

## Build

Requirements: **Android Studio Ladybug (2024.2) or newer** (or JDK 17 + Android SDK 35). The first build needs internet access to download Gradle and the libraries. The app itself never uses the internet.

### Android Studio

1. Unzip the source (or clone the repo) and choose **File → Open** on the project folder.
2. Let Gradle sync finish.
3. Pick a device or emulator (Android 8.0 / API 26 or newer) and press **Run**.

### Command line

```bash
./gradlew testDebugUnitTest   # unit tests for money, budgets, goals, debts, forecasts and reports
./gradlew assembleDebug       # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease     # app/build/outputs/apk/release/app-release.apk
```

Without a `keystore.properties`, the release APK is signed with your local **debug key**. That's fine for installing on your own phone. To sign with your own key, create `keystore.properties` in the project root (it's git-ignored):

```properties
storeFile=/absolute/path/to/mymoney.jks
storePassword=...
keyAlias=mymoney
keyPassword=...
```

A GitHub Actions workflow (`.github/workflows/android.yml`) runs the tests and uploads both APKs as build artifacts on every push, if you host the repo on GitHub.

The PNG and ICO icons and `gradle-wrapper.jar` are binary, so they go missing if the repo is filled through the GitHub API or website. `.github/workflows/restore-binaries.yml` regenerates any that are absent, from `desktop/assets/*.svg` and the Gradle version pinned in `gradle-wrapper.properties`, and commits them. Run it from the **Actions** tab if you ever need it again.

## Install the APK on your phone

1. Copy `app-release.apk` (or `app-debug.apk`) to the phone, for example over USB or a local file share.
2. Open it with the Files app. When asked, allow **Install unknown apps** for that app (Settings → Apps → Special access → Install unknown apps).
3. Tap **Install**, then open MyMoney.

Or with USB debugging enabled: `adb install -r app/build/outputs/apk/release/app-release.apk`.

The debug build uses the application id `com.mymoney.app.debug`, so it installs alongside the release build.

## Review the design in a browser

The `preview/` folder contains an interactive, dependency-free representation of the intended Android UI. It is useful for reviewing the visual design before installing the APK:

```bash
python3 -m http.server 43127 --directory preview
```

Open `http://127.0.0.1:43127`. The bottom navigation, light/dark themes and quick-add sheet are interactive. This is a design preview, not the Android app or a replacement for device testing.

## Backups

- **Settings → Create encrypted backup** writes a `.mymoney` file wherever you choose (Downloads, SD card, USB). It contains all data, settings and receipt photos, encrypted with your password. There is no password recovery.
- **Restore from backup** replaces all current data after showing a summary and asking you to confirm.
- **Export all data (JSON)** is readable and unencrypted, without photos. Use it for your own records or to move data elsewhere.

## Project layout

```
app/src/main/java/com/mymoney/app/
  core/            Money (cents/BigDecimal), dates, finance math (safe-to-spend, goals, debts, forecasts, bills), reports
  data/db/         Room entities, DAOs, database
  data/repo/       FinanceRepository (all writes are transactional), default categories, receipt storage
  data/prefs/      DataStore settings
  data/backup/     AES-GCM backup crypto + backup/restore
  data/export/     PDF (android.graphics.pdf) and CSV export
  notifications/   Bill reminders (AlarmManager + boot receiver)
  ui/              Compose screens by feature, shared components, theme and navigation
app/src/test/      JVM unit tests for the core logic
```

## Status and known limitations

- **Not compiled in the authoring environment.** This code was written in a sandbox with no access to Google Maven or Maven Central, so the Android Gradle build, the APK and on-device testing could not be done there. Run `./gradlew assembleDebug` on a machine with internet access and fix any compile issues before relying on the app.
- **What was verified:** the money parsing, safe-to-spend, bill scheduling, goal, debt amortisation, forecast and report logic, plus the Room entities, were compiled with Kotlin 2.0 and pass **27 JUnit tests**. Every source file also parses without syntax errors.
- Reminders use inexact daily alarms (around 09:00), so they can arrive a little late, depending on the phone's battery optimisation.
- Debt interest uses simple daily interest (actual/365) for repayment splits and monthly interest for projections. Your lender's exact method may differ slightly, so you can edit the interest part of each repayment.
- Currency is a display setting. Multiple currencies at once and exchange rates are not supported.
- The UI is English only.
