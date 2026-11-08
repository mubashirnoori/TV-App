# Rummish Saleem TV
Apple TV-inspired, remote-first Android TV launcher for the **PTCL ZTE B760HS3** (Android 7.1.2 / API 25, 1 GB RAM).
Pure Java, no AndroidX/Kotlin/Compose. Only dependency: ExoPlayer (core + HLS) for IPTV.

## STATUS - read first
**Review pass 2 (this revision):** see `CHANGES.md`. The APK was **NOT built** in the review sandbox (no Android SDK, no access to Google/Maven).
Build it with GitHub Actions or Android Studio (section 1); nothing below has been run on a real box.

| Item | State |
|---|---|
| Java source (11 files) | Written and **type-checked against the real Android API 25 `android.jar`** (0 errors). |
| ExoPlayer calls | Checked against my own stubs only; verified for real when Gradle resolves ExoPlayer 2.19.1. |
| Gradle build / resources (aapt) | **Not run** - the authoring sandbox cannot reach Google/Maven. First real build = yours (below). |
| Run on a real ZTE B760HS3 | **Not tested.** Treat 1.0.0 as a first build; use the key tester and recovery steps. |
| Signed APK | Produced by the included GitHub Actions workflow (or Android Studio). Not pre-built here. |

## 1. Get the APK (pick one)
**A. GitHub Actions (no PC setup)**
1. Create a private GitHub repo, upload this folder's contents, push to `main`.
2. Actions tab > "Build signed APK" > wait ~5 min > download artifact `RummishSaleemTV-release-apk`.
3. Keep updates installable: create ONE keystore once, then add repo secrets `KEYSTORE_BASE64` (`base64 -w0 release.jks`) and `KEYSTORE_PASSWORD`.
   Without them each build uses a throw-away key, and a newer build will not install over an older one (uninstall first).
   Create keystore: `keytool -genkeypair -keystore release.jks -alias rummish -keyalg RSA -keysize 2048 -validity 10000`
   (alias must be `rummish`, key password = store password).

**B. Android Studio:** open the folder (Hedgehog or newer; it creates the Gradle wrapper), put `release.jks` in `app/`,
set env `KS_PASS`, then Build > Generate Signed APK, or run `gradle assembleRelease`. Output: `app/build/outputs/apk/release/`.

## 2. Install on the box
- **USB:** copy the APK to a FAT32/exFAT stick, open it with a file manager on the box (allow "Unknown sources" in Settings > Security).
- **ADB (best):** Settings > About > tap *Build number* 7x > Developer options > USB/Network debugging, then
  `adb connect <box-ip>:5555` and `adb install -r RummishSaleemTV.apk`.

## 3. Make it the default launcher (PTCL launcher stays installed)
1. Open the app once from the app list (or press the Menu button on its home screen > *Set as default launcher*).
2. Press **HOME** on the PTCL remote. If Android shows a chooser, pick *Rummish Saleem TV* and **Always**.
3. If the PTCL firmware hides the chooser: Settings > Apps > (current PTCL launcher) > *Clear defaults*, then press HOME again.

## 4. Recovery (go back to PTCL)
1. **From inside the app:** press **Menu** > *Return to original PTCL launcher*, or *Stop being the default launcher* (then HOME shows the chooser).
2. **Settings route:** Settings > Apps > Rummish Saleem TV > *Clear defaults* (or *Uninstall*).
3. **ADB:** `adb uninstall com.rummish.saleemtv`.
4. **If the box seems stuck:** enable ADB beforehand (step 2 above) - this is the safest lifeline. Factory reset is the last resort and wipes the box.
Nothing here is rooted, no firmware is touched, no system app is removed.

## 5. Remote control
D-pad moves a visible white focus ring between tiles (rows scroll automatically). **OK** opens; **Back** backs out
(Home screen ignores Back). **Home** returns to the launcher. **Menu** (or INFO / YELLOW) opens the menu.
**Hold OK** or **RED** adds/removes a favorite. In IPTV: Up/Down = previous/next channel, OK/Left/Right = channel list,
CH+/CH- zap, Menu = sources, Volume = system. Remote buttons differ per model: Menu > **Remote key tester** shows what each
PTCL button sends; if a button is missing, tell me the code and I will map it.

## 6. What each feature really does
- **App tiles:** YouTube, Netflix, Tapmad, Tamasha, VLC, Prime Video, Chrome, Settings are detected from installed apps
  (known package names, then exact/partial label match). Real icons come from the installed app. Missing app = dimmed tile +
  "Not installed" message. If launching throws, an "incompatible" message is shown. Nothing copyrighted is bundled.
  Tapmad/Tamasha package names are unknown to me, so they are label-matched.
- **IPTV Player:** M3U/M3U8 (also gzip), Xtream Codes (via its standard `get.php` M3U + `xmltv.php`), categories from `group-title`,
  favorites, search, EPG now/next (XMLTV, streaming parser, only now/next kept in RAM), cached offline copy of each list.
  Default sources are the community **iptv-org** public lists (Pakistan, News, Sports, Entertainment, Kids, Religious, Music, General).
  Many streams in such lists go offline; some may be geo-restricted. You are responsible for lawful use. No paid-channel lists included.
  EPG needs `tvg-id` in the playlist matching the guide; free lists often have none, so EPG is mainly useful with your own Xtream/M3U provider.
  Playlist cap 3000 channels (RAM). Playback: ExoPlayer, 25 s max buffer, 8 MB buffer cap, released when the screen is hidden.
- **Kids Zone:** offline ABC (with pictures and voice if the box has a TTS engine), Urdu alphabet (38 letters; needs the box's
  Arabic-script font), math game. Video categories (Cartoons, Educational, Movies, Islamic) are **empty until a parent sets a source**;
  Parent Settings (4-digit PIN, salted SHA-256) lets parents set a playlist per category, hide categories, change PIN.
  A suggested public iptv-org list is *prefilled for review* - those lists are not vetted for children; prefer your own curated M3U.
  Limit: Android does not let a launcher block the Home button, so this is a parental *content gate*, not a kiosk lock.
- **Boot/Splash:** the animated black "Apple TV / Developed by Rummish Saleem" splash plays when the launcher process starts.
  The *real* boot logo (bootloader `logo` partition) and `bootanimation.zip` need root or a signed firmware flash; wrong writes can
  brick the box, so this project does not touch them. Only PTCL/ZTE can supply a legitimate firmware change.

- **Live wallpaper & widgets (home screen):** slow-moving aurora background plus Continue-watching / Weather / This-box cards. Both can be switched off (Menu, or Settings > Display & Sound). Weather needs internet; city is set in Settings.

- **Digital TV (the Faisal app):** install its APK once (USB stick > open **Files** tile > tap the .apk > allow Unknown sources). The **Digital TV** tile then appears automatically. To open it at power-up: Settings > Launcher > *On power-up open* > Digital TV. Its landscape/remote behaviour is whatever the app itself provides (see CHANGES.md, Update 6).

## 7. AirPlay - honest assessment
There is **no AirPlay receiver in this APK** and I did not fake one. Reasons: AirPlay receiving needs the FairPlay handshake
(reverse-engineered, not available as a maintained Android library), an mDNS/Bonjour responder, H.264 mirroring decode and AAC-ELD audio;
on 1 GB RAM / Android 7.1 / a weak SoC, mirroring is marginal even in dedicated apps.
What the tile does: it launches an **installed AirPlay-capable receiver app** (detected by package/label, e.g. AirScreen), otherwise explains this.
Realistic plan: install a receiver such as AirScreen (free, ads) and test photo, video casting, then mirroring; expect video/photo to work
better than mirroring. Alternative that works on any iPhone without AirPlay: YouTube/Netflix/Prime have their own Cast support
if the box also runs Google Cast (the PTCL box may not). Open-source receivers (RPiPlay, UxPlay) target Linux, not this box.

## 8. Compatibility & performance decisions (API 25)
- `minSdk=targetSdk=25`; no adaptive icons (26+), no notification channels, no `getMainExecutor` (28), no `Typeface` weights (28), no PiP (26), no AndroidX.
- Leanback launcher category + HOME category + DEFAULT; `singleTask`; not `android:persistent`; no services, no boot receiver, no background work.
- UI built from plain Views sized in "1080p units", so it scales to 720p/1080p/4K. One 120 ms scale animation on focus; splash uses 2 alpha fades.
- App detection runs once on a background thread (re-checked at most every 15 s when returning); icons come straight from PackageManager (no bitmap cache of our own).
- IPTV: playlist streamed from disk, EPG reduced to now/next, player destroyed in `onStop`.
- No zero-buffering promise: network quality and the stream source dominate.

## 9. Known risks / to verify on the box
1. First build may need minor Gradle fixes (not compiled in my sandbox).
2. HTTPS lists need the box's root certificates to be current; if lists fail with SSL errors, use an `http://` source or a local file path.
3. PTCL's Home key handling may force its own launcher on boot; some operator firmwares ignore third-party HOME apps.
4. Emoji/Urdu glyph rendering depends on the box fonts.

## 10. Logo
`drawable/logo_mark.xml` is an original mark (rounded-screen TV with a play button). The splash shows "Rummish Saleem TV" with it;
no Apple name or logo is used anywhere. Replace the drawable to change the mark everywhere (splash, launcher icon, banner).
