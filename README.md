<a id="top"></a>

<p align="center">
  <img src="app/src/main/ic_launcher-playstore.png" width="128" height="128" alt="SteamNova logo">
</p>

<h1 align="center">SteamNova</h1>

<p align="center">
  <strong>Chat, customize your profile, explore SteamDB data, and manage Steam Guard — inside Steam for Android.</strong><br>
  An Android Steam enhancement module powered by modern libxposed.
</p>

<p align="center">
  <a href="README_zh.md">简体中文</a> •
  <a href="https://goodjoe-2049.github.io/Steam-Nova/">Website</a> •
  <a href="#features">Features</a> •
  <a href="#chat">Chat</a> •
  <a href="#profile">Profile</a> •
  <a href="#steam-guard">Steam Guard</a> •
  <a href="#mafile">maFile &amp; Restoration</a> •
  <a href="#quick-start">Quick Start</a> •
  <a href="https://github.com/GoodJoe-2049/Steam-Nova/issues">Feedback</a>
</p>

<p align="center">
  <a href="https://github.com/GoodJoe-2049/Steam-Nova/releases/latest"><img src="https://img.shields.io/github/v/release/GoodJoe-2049/Steam-Nova?style=flat-square&amp;label=release&amp;labelColor=334155&amp;color=3B82F6" height="20" alt="Latest release — download APKs"></a>
  <a href="https://github.com/GoodJoe-2049/Steam-Nova/releases"><img src="https://img.shields.io/github/downloads/GoodJoe-2049/Steam-Nova/total?style=flat-square&amp;label=downloads&amp;labelColor=334155&amp;color=0D9488" height="20" alt="Total release asset downloads"></a>
  <a href="https://github.com/GoodJoe-2049/Steam-Nova/commits/main/"><img src="https://img.shields.io/github/last-commit/GoodJoe-2049/Steam-Nova/main?style=flat-square&amp;label=last%20commit&amp;labelColor=334155&amp;color=8B5CF6" height="20" alt="Last commit on main"></a>
  <a href="https://t.me/MillenniumXposed"><img src="https://img.shields.io/badge/Telegram-229ED9?style=flat-square&amp;logo=telegram&amp;logoColor=white" height="20" alt="SteamNova Telegram channel"></a>
  <img src="https://img.shields.io/badge/Android-8.0%2B-16A34A?style=flat-square&amp;labelColor=334155&amp;logo=android&amp;logoColor=white" height="20" alt="Android 8.0 or later">
  <img src="https://img.shields.io/badge/libxposed-API%20102-6366F1?style=flat-square&amp;labelColor=334155" height="20" alt="libxposed API 102">
  <img src="https://img.shields.io/badge/ABI-arm64--v8a-64748B?style=flat-square&amp;labelColor=334155" height="20" alt="arm64-v8a">
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-MIT-0F766E?style=flat-square&amp;labelColor=334155" height="20" alt="MIT License"></a>
</p>

<table align="center">
  <tr>
    <td width="25%" align="center" valign="top">
      <a href="docs/images/panel-witcher3.jpg"><img src="docs/images/panel-witcher3.jpg" width="280" alt="SteamDB panel on The Witcher 3 store page"></a><br>
      <sub>SteamDB panel</sub>
    </td>
    <td width="25%" align="center" valign="top">
      <a href="docs/images/steam-menu-preview.jpg"><img src="docs/images/steam-menu-preview.jpg" width="280" alt="SteamNova entry in the Steam menu"></a><br>
      <sub>Steam menu entry</sub>
    </td>
    <td width="25%" align="center" valign="top">
      <a href="docs/images/module-settings-preview.jpg"><img src="docs/images/module-settings-preview.jpg" width="280" alt="SteamNova General page with the Liquid Glass navigation bar"></a><br>
      <sub>Module settings</sub>
    </td>
    <td width="25%" align="center" valign="top">
      <a href="docs/images/steam-guard-export-preview.jpg"><img src="docs/images/steam-guard-export-preview.jpg" width="280" alt="Steam Guard export dialog with sensitive data obscured"></a><br>
      <sub>Steam Guard export</sub>
    </td>
  </tr>
</table>

<p align="center"><sub>Some screenshots are from earlier releases and may show the previous name or interface.</sub></p>

---

**SteamNova** is an enhancement module for Steam on Android. It adds built-in chat, desktop-style profile showcases, local profile customization, SteamDB statistics, and Steam Guard tools. Open **Steam → Menu → SteamNova**, above **Store**, to manage the module.

<a id="rename"></a>

> **Millennium is now SteamNova.** The project has a new name, icon, and repository: [GoodJoe-2049/Steam-Nova](https://github.com/GoodJoe-2049/Steam-Nova).
>
> This project has **no connection to [SteamClientHomebrew/Millennium](https://github.com/SteamClientHomebrew/Millennium)**. It is not its Android version, an official port, or an affiliated project.
>
> The standalone module package changed from `com.millennium.app` to `steam.nova.app`. When upgrading from the old module, enable **SteamNova** for **Steam** in your framework, disable the old module, then force-stop and reopen Steam.

<a id="features"></a>

## ✨ Features

| Feature | What it offers |
| :--- | :--- |
| **Built-in chat** | Private and group chats, images, stickers, emoticons, reactions, and group management without installing Steam Chat. |
| **Chat tools** | Pinned and muted conversations, message caching, saved drafts, friend nicknames, and background notifications. |
| **Chat room effects** | Native playback and replay using Steam's official effect resources, with persistent resources and a parsed cache. |
| **Better Profile** | Desktop-style showcases with two-column item layouts where applicable, a complete profile header, and a left sidebar for profile details. |
| **Profile customization** | Preview and save backgrounds, avatars, avatar frames, special profiles, and mini profile backgrounds locally. Saved avatars also appear across Steam's native pages and built-in chat. |
| **Guest browsing** | Browse public Steam pages without signing in. Account features still require login. |
| **SteamDB floating panel** | Current players, 24-hour and all-time peaks, followers, historical lows, SteamDB rating, and the last update date. |
| **Price history** | Prices for the store's currency and region, with discounts, recorded occurrences, and dates where available. Includes the 2-year low when supplied by SteamDB. |
| **Floating controls** | Drag the floating button, snap it to an edge, and retain its position. Refresh the current game's data or open its SteamDB page. |
| **Liquid Glass navigation** | Separate controls for the module and Steam bottom bars, with glass effects and animated interactions on Android 13 or later. |
| **Library pinch zoom** | Pinch to change the game-library layout with a transition inspired by Telegram's media page. |
| **Steam Guard tools** | Choose the default Guard page, preview and copy the next code, and export authenticator details. |
| **QR codes and direct import** | Export to Stratum, Aegis, or the Monica authenticator using the matching QR format or an “Add to” action. |
| **maFile backup and restoration** | Import, export, and restore account data; request maFiles directly from compatible versions of Monica Steam. |
| **Steam management** | Use Shizuku/Sui, Dhizuku, or Root to uninstall Steam while retaining local app data. |
| **In-app settings and language** | A miuix settings interface with Follow System, English, and Simplified Chinese language options. |

<a id="chat"></a>

## 💬 Built-in chat & room effects

Enable **General → Built-in chat**, then **force-stop and reopen Steam**. This feature is off by default.

- **Conversations:** private messages and group chats, images, stickers, emoticons, reactions, and group management.
- **Everyday use:** pinned chats, Do Not Disturb, friend nicknames, message caching, saved drafts, and background message notifications.
- **Navigation:** chat becomes a bottom-bar tab, and notifications move to the top right of the Store page. Conversation detail pages hide the bottom bar and adjust to the keyboard.
- **Room effects:** send effects, automatically play newly received effects in an active conversation, or tap a message to replay one.

Room effects use Steam's official JavaScript, CSS, and image resources to prepare native playback, including timing, random variations, and layout adaptation. The first preparation needs a network connection and can take a while. Downloaded effect resources and parsed results are stored persistently, so Android's **Clear cache** does not normally remove them or require a full parse again. Invalid or incompatible cached data is rebuilt, and an update failure can fall back to previously validated resources. **Clear storage/data** or uninstalling without retaining data removes this protection.

**Voice calls are not currently supported.** Background notifications depend on Steam remaining able to run and on Android's notification permissions and background restrictions.

<a id="profile"></a>

## 🎨 Profile layout & customization

These are two independent options under **General → Appearance**. Both are off by default; restart Steam after changing either switch.

### Better Profile

- Use desktop-style showcases, including two items per row where the desktop showcase supports it.
- Move the desktop right column—level, badges, online status, profile details, and related links—into a sidebar opened from the upper left.
- Show the profile header information together, with a compact avatar beside the name and location, without the mobile “View more info” step.

The feature adapts the profile layout for the phone; it does not require desktop-device emulation.

### Profile customization

Enable **Profile customization**, then open your profile and tap the **palette icon** that replaces the avatar at the top right.

- Browse **backgrounds, avatars, avatar frames, special profiles, and mini profile backgrounds** in a panel that slides up from the bottom.
- Search by item name or filter by game / App ID, refresh the catalog, and expand or collapse the panel.
- Preview an item, choose **Save on device**, or **Restore original**. Closing the panel discards an unsaved preview.
- Saved avatars also appear on Store, News, Steam Guard, Chat, Menu, and Notifications pages. Top-right avatars stay static; the account page with **View profile** supports animated avatars and avatar frames.

**Customization changes what you see on this device.** It does not purchase or equip items on Steam's servers, and other users do not see these local changes.

<a id="interface"></a>

## 📱 Interface & interaction

The module settings use [miuix](https://github.com/compose-miuix-ui/miuix) components for cards, switches, dialogs, and selection menus. Tap the bottom tabs or swipe between pages.

Under **General → Appearance**, open **Liquid Glass navigation bar** to control the **module** and **Steam** bottom bars separately. Android 13 or later is required; earlier versions use regular navigation bars. **Library pinch zoom** is enabled by default; restart Steam after changing it.

Guest browsing works by default and has no separate switch. You can browse public Store and community content without signing in, then sign in when needed. Account-specific features still require login.

The **SteamDB floating panel** switch takes effect immediately. **Settings → Language** changes the module's language without changing Steam's own language. The settings interface refreshes immediately; injected interfaces such as chat and the next Steam Guard code display require a Steam restart to refresh their language.

**Themes** and **Logs** are currently placeholder pages.

<a id="steam-guard"></a>

## 🔐 Steam Guard tools & export

Under **General**, choose whether Steam Guard opens to the **code** or **QR scanner** page, and enable **Show next Steam Guard code** to preview and copy the next code. Restart Steam after changing these options.

For export, open **Steam → Menu → SteamNova → General → Export Steam Guard**.

- **Tap to copy:** view the authenticator type, username, issuer, and secret; select an account when multiple accounts are available.
- **Scan a QR code:** choose Stratum, Aegis, or Monica to show the matching import format. QR changes use an animated transition.
- **Add directly:** tap **Add to Stratum**, **Add to Aegis**, or **Add to Monica** to open the chosen authenticator with the account details.

| Authenticator | Expected direct-import flow |
| :--- | :--- |
| [Stratum](https://github.com/stratumauth/app) | Opens the app to add a Steam authenticator; unlock it if prompted. |
| [Aegis](https://github.com/beemdevelopment/aegis) | Opens a prefilled entry for review and saving; unlock it if prompted. |
| Monica authenticator | Opens Monica with the Steam authenticator details for import. This is separate from Monica Steam's maFile integration below. |

SteamNova selects the appropriate Steam link format for both QR codes and direct import. Install and initialize the chosen authenticator first; its confirmation flow can vary by version.

QR codes are generated on the device. Authenticator data is not automatically copied to the clipboard when Steam starts. If no data is available yet, open Steam's **Steam Guard** page first, then return to the export dialog.

<a id="mafile"></a>

## 📦 maFile import, export & restoration

Open **Steam → Menu → SteamNova → General → maFile import and export**.

### Export and compatibility

- Select an account when more than one is available, then tap **Export file** to save a `.maFile`.
- View the username, SteamID, authenticator secrets, device ID, revocation code, and login tokens when available. **Tap a field to copy its complete value.** Long values use a single-line ellipsis; copying and exporting preserve the full content.
- Export matches authenticator data and login credentials by SteamID. Additional maFile fields are retained when importing and re-exporting.
- Supports the single-account plaintext maFile format used by **Monica Steam** and **Steam Desktop Authenticator (SDA)**. Import accepts files up to **1 MB**; ZIP archives and encrypted backups are not supported.
- **Earlier releases verified Monica Steam interoperability and same-account restoration across phones**, including matching Steam Guard codes and login approvals with a valid session. To export to Monica Steam, save the maFile and select it in Monica Steam's import screen.

### Import directly from Monica Steam

In the **Import** tab, choose **Get from Monica Steam**. A compatible Monica Steam version lets you select an account and authorize the transfer, then returns you to SteamNova to review and import the maFile. No manual file transfer is needed.

If the installed Monica Steam version does not support this handoff, export a maFile from it and use **Choose file** instead. The integration is documented in [Monica Steam maFile handoff](docs/monica-mafile-handoff.md).

**Monica Steam** manages Steam accounts and maFiles. The **Monica authenticator** in the QR export dialog is a different app.

### Restore in the official Steam app

1. On the source phone, open Steam's **Steam Guard** page, then export the account's maFile through SteamNova.
2. On the destination phone with SteamNova active, open the **Import** tab, choose the file, review the account, and tap **Import**.
3. Import replaces saved data for the same **SteamID** and automatically queues restoration when the host and credentials are supported. A matching account appears only once in the list.
4. **Force-stop Steam in Android Settings, then reopen it.** Returning from the background does not apply restoration.
5. Check the restore status and Steam's **Steam Guard** page. A valid session for that account is required for online login approvals.

If authenticator data is available but login credentials are missing, expired, or invalid, SteamNova restores the authenticator and keeps the current login session. A file containing only usable login credentials can restore the session, but cannot supply missing authenticator secrets. Steam validates login credentials online.

Other account records are preserved. A device ID included in the file also updates Steam's global device ID. You can queue multiple accounts before restarting, cancel a pending restore, or use **Restore account data** to schedule it again.

Imported accounts and restoration backups are encrypted in private app storage. Failed writes trigger rollback; interrupted restores are not automatically replayed. **Undo** restores the saved previous state only when the current data still matches the restore record. After a successful restore, exports use Steam's current session credentials.

<a id="steam-management"></a>

## 🧰 Steam management

Select **Settings → Working mode** and authorize one of the available modes:

| Mode | Preparation |
| :--- | :--- |
| **Shizuku / Sui** | Start the service and grant access to the Steam host. |
| **Dhizuku** | Activate Dhizuku, start its service, and authorize the request. |
| **Root** | Grant superuser access to Steam through your root manager, such as Magisk or KernelSU. |

These optional modes enable privileged management operations; they do not replace the Xposed framework or need to be enabled for ordinary module features.

Under **General → Steam Management**, **Uninstall Steam and keep data** removes Steam while retaining its app data for the current Android user. Steam closes when the operation starts. Reinstalling with retained data requires a matching signing certificate; official and NPatch-patched builds may use different signatures. Keeping app data is not a substitute for a maFile backup.

<a id="quick-start"></a>

## 🚀 Quick Start

### Requirements

| Item | Requirement |
| :--- | :--- |
| Android | Android 8.0 / API 26 or later, subject to the framework's and Steam's own requirements. |
| Architecture | `arm64-v8a`. |
| Framework for the standalone module | LSPosed or a compatible framework whose **core supports modern libxposed API 102**. |
| Target app | Steam for Android: `com.valvesoftware.android.steam.community`. |
| Current Steam adaptation | Steam **3.10.11 (10928360)**. Features that depend on Steam's internal interface or bundle may need updates for other versions. |
| Optional effect | Android 13 or later for Liquid Glass. |
| Optional management mode | Authorized Shizuku/Sui, Dhizuku, or Root for privileged Steam management. |

### Downloads

[GitHub Releases](https://github.com/GoodJoe-2049/Steam-Nova/releases/latest) provides the latest files and release notes. The [v1.4.7 release](https://github.com/GoodJoe-2049/Steam-Nova/releases/tag/v1.4.7) includes:

| File | Purpose |
| :--- | :--- |
| `SteamNova-v1.4.7-arm64-v8a.apk` | Standalone module for the framework installation below. |
| `Steam-1.4.7-npatched.apk` | NPatch-patched Steam APK, distributed separately from the standalone module. Follow its release instructions. |

### Framework installation

1. Download and install the **standalone module APK** from [GitHub Releases](https://github.com/GoodJoe-2049/Steam-Nova/releases).
2. Enable **SteamNova** in your Xposed framework manager.
3. Select **Steam** in the module scope. If upgrading from Millennium, disable the old module.
4. **Force-stop Steam and reopen it** to load the module.
5. Open Steam's rightmost **Menu** tab, then select **SteamNova** above **Store**.
6. Enable **Built-in chat**, **Better Profile**, or **Profile customization** as needed, then restart Steam. These features are off by default.
7. Visit a game's store page and tap the floating button for SteamDB data. Steam Guard and maFile tools are on the module's **General** page.

The floating panel belongs to Steam's own window and does not require Android's “Display over other apps” permission.

For an existing local Git checkout after the repository rename, run the following **inside that repository directory**:

```bash
git remote set-url origin https://github.com/GoodJoe-2049/Steam-Nova.git
git remote -v
```

The local folder name does not need to change.

<a id="data"></a>

## 📊 Data sources

- **Current players:** Steam's official API, with SteamDB as a fallback.
- **Peaks, followers, and last update:** SteamDB game information.
- **Historical lows:** SteamDB pricing data for the current store currency and region.
- **SteamDB rating:** calculated locally from the store page's positive and negative review counts, using the SteamDB formula.
- **Chat room effects:** official Steam effect scripts, styles, and image resources, prepared for native playback.
- **Profile customization:** Steam's item catalog and media resources; selections are applied locally.

Availability depends on the upstream services and the current Steam page. Missing fields are shown as unavailable instead of being treated as zero.

<a id="faq"></a>

## 💬 FAQ

<details>
<summary><strong>Why is the menu entry or floating button missing?</strong></summary>

Check that the module is enabled, Steam is selected in its scope, and the framework core supports API 102. Force-stop Steam and open it again. If the SteamNova entry is present, check the SteamDB floating panel switch.

Menu detection currently recognizes the Chinese “商店” and English “Store” labels. Changing the module language does not add support for other Steam menu languages. Steam updates may also require compatibility changes.

</details>

<details>
<summary><strong>Why are some statistics unavailable or different from what Steam shows?</strong></summary>

Steam and SteamDB are separate sources and may have different availability or update times. Prices follow the store's currency and pricing region, not the phone's language. The SteamDB rating accounts for review count and is not the same as Steam's positive-review percentage.

</details>

<details>
<summary><strong>Why does direct import fail to open an authenticator?</strong></summary>

Install and enable the selected app in the same Android user or profile as Steam. Check any system restrictions on launching other apps. If the authenticator is locked, complete its password or biometric prompt. You can also use the QR code or copy the individual fields.

</details>

<details>
<summary><strong>Why did importing a maFile not immediately restore Steam Guard?</strong></summary>

Restoration runs at the next full Steam startup. Check that the dialog shows a queued restore, then force-stop Steam in Android Settings and reopen it. Importing another file for the same SteamID replaces that account's saved data and pending request.

If restoration is unavailable, the file can still be saved and re-exported. A login-only file cannot restore missing authenticator secrets; login approvals also need a valid session for the account. Include the restore status and Steam version when reporting a problem.

</details>

<details>
<summary><strong>What is the difference between the two Monica integrations?</strong></summary>

**Add to Monica** in Steam Guard export opens the Monica authenticator with a Steam token entry. **Get from Monica Steam** in maFile import requests a maFile from a compatible Monica Steam app after authorization. To send a maFile in the other direction, export it and select it in Monica Steam's import screen.

</details>

<details>
<summary><strong>Why is chat missing, or why are there no background notifications?</strong></summary>

Turn on **General → Built-in chat**, then force-stop and reopen Steam. Check the supported Steam version, Android notification permissions, and background restrictions. A force-stopped Steam process cannot deliver background chat notifications.

</details>

<details>
<summary><strong>Why does preparing room effects take time?</strong></summary>

The first preparation downloads and parses Steam's effect resources. Later starts reuse validated resources and parsed results from persistent storage. Android's **Clear cache** normally preserves these files; clearing app data does not. Changes to upstream resources or an invalid cache may require another preparation.

</details>

<details>
<summary><strong>Can other people see my profile customization?</strong></summary>

No. Saved profile customization changes only the display on this device. It does not modify the items equipped on your Steam account. Use **Restore original** to return to the original appearance.

</details>

<details>
<summary><strong>Why do I still see Millennium in some names or links?</strong></summary>

SteamNova is the new project name. The Telegram channel retains its existing address, and some historical screenshots, internal storage names, and diagnostic tags still use Millennium. This does not indicate a relationship with SteamClientHomebrew/Millennium.

</details>

<a id="build"></a>

## 🛠️ Build from source

```bash
git clone https://github.com/GoodJoe-2049/Steam-Nova.git
cd Steam-Nova
```

Open the project in Android Studio and sync Gradle. The current configuration uses **Android SDK 37**, a **JDK 25 Gradle daemon toolchain**, and **Java 21 source/target compatibility**. Use the repository's Gradle Wrapper; dependency versions are defined in `gradle/libs.versions.toml`.

<details>
<summary><strong>Build commands and APK output</strong></summary>

Windows / PowerShell:

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:assembleRelease
```

Linux / macOS:

```bash
chmod +x gradlew
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
```

Outputs are written to `app/build/outputs/apk/debug/` and `app/build/outputs/apk/release/`. Release filenames follow `SteamNova-v<versionName>-<ABI>.apk`, for example `SteamNova-v1.4.7-arm64-v8a.apk`.

Release builds enable R8 and resource shrinking. Keep the provided ProGuard rules. An unsigned release APK must be signed before installation; Android Studio's **Generate Signed App Bundle or APK → APK** can do this.

</details>

<a id="contributing"></a>

## 🤝 Feedback & contributions

[Issues](https://github.com/GoodJoe-2049/Steam-Nova/issues) and [pull requests](https://github.com/GoodJoe-2049/Steam-Nova/pulls) are welcome. Include the device and Android version, framework and module versions, Steam version, reproduction steps, and relevant logs. For store-data issues, include the game's store link or AppID.

Follow the [Telegram channel](https://t.me/MillenniumXposed) for release announcements and project updates.

Exported maFiles contain plaintext credentials, and session diagnostic logs can include login tokens. Before sharing screenshots or logs, redact account details, authenticator secrets, access/refresh tokens, and QR codes. Do not attach an unredacted maFile to an issue.

## 🙏 Acknowledgements

- [SteamDB Browser Extension](https://github.com/SteamDatabase/BrowserExtension) — data fields, regional pricing, and rating calculations.
- [libxposed](https://github.com/libxposed/api) — the modern Xposed module API.
- [miuix](https://github.com/compose-miuix-ui/miuix) — module settings components and interactions.
- [AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass) — Liquid Glass effects.
- [Stratum](https://github.com/stratumauth/app) and [Aegis](https://github.com/beemdevelopment/aegis) — open-source authenticator apps.
- [Steam Desktop Authenticator](https://github.com/Jessecar96/SteamDesktopAuthenticator) — references for Steam maFile, Steam Guard, and trade confirmations.
- [steamguard-cli](https://github.com/dyc3/steamguard-cli) — references for Steam Guard sign-in, authenticator transfers, and confirmation protocols.
- [AnotherVaporAuth](https://github.com/freefrank/AnotherVaporAuth) — references for the Steam mobile authenticator and login approvals.
- [Monica Steam](https://github.com/JoyinJoester/Monica-Steam) and the [Monica authenticator](https://github.com/Monica-Pass/Monica) — Steam account interoperability and authenticator import support.
- [Steam Profile Modifier](https://chromewebstore.google.com/detail/steam-profile-modifier/lfjdaphbejfpncjfebkbcganmkinhhfl) — a reference for local profile customization.
- [Lottie Web](https://github.com/airbnb/lottie-web) — animations in the profile customization panel.

## 📄 License

SteamNova is licensed under the **[MIT License](LICENSE)**. Third-party code, dependencies, and assets retain their respective licenses.

This is an independent community project and is not affiliated with Valve, Steam, or SteamDB.

---

<p align="center">
  <a href="https://github.com/GoodJoe-2049/Steam-Nova/releases">Releases</a> •
  <a href="https://github.com/GoodJoe-2049/Steam-Nova/issues">Issues</a> •
  <a href="https://t.me/MillenniumXposed">Telegram</a> •
  <a href="#top">Back to top ↑</a>
</p>
