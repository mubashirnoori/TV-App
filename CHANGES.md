# Review pass - what was missing / broken, and what was changed
Only defects and gaps were touched; design and working features are unchanged.

| # | Problem found | Fix |
|---|---|---|
| 1 | `android.useAndroidX=false` but ExoPlayer 2.19.1 depends on `androidx.annotation` -> AGP fails the build | `android.useAndroidX=true` (gradle.properties). No AndroidX UI libs are used. |
| 2 | No Gradle wrapper (README says "Android Studio creates it"; CLI builds impossible) | Added `gradlew`, `gradlew.bat`, `gradle/wrapper/*` (Gradle 8.5, matches CI) |
| 3 | A stream error only showed a message; no recovery | Auto-retry 3x with back-off ("Connection lost - retrying"), counter resets when playback is ready |
| 4 | HLS URLs without `.m3u8` were treated as progressive and failed | On container-parse error, retry once with HLS forced |
| 5 | `#EXTVLCOPT:http-referrer / http-user-agent` lines (used by iptv-org) ignored -> those channels 403 | Parsed into `Channel.ua/referer`, applied per channel |
| 6 | Hardware decoder rejection on a weak SoC = dead channel | `setEnableDecoderFallback(true)` |
| 7 | Wi-Fi may sleep during long playback | `setWakeMode(WAKE_MODE_NETWORK)` + `WAKE_LOCK` permission |
| 8 | README says "Hold OK = favorite", but ListView never delivers a long-press for the remote OK key | Hold-OK detection in `IptvActivity.dispatchKeyEvent`; the click that follows is swallowed |
| 9 | Media keys missing | PLAY, PAUSE, STOP handled in IPTV |
| 10 | Kids/Learn/KeyTest recreated when a Bluetooth remote/keyboard connects (no `configChanges`) | Added `configChanges` in manifest |
| 11 | On-screen keyboard could pop up in IPTV (search box) | `windowSoftInputMode=stateAlwaysHidden|adjustPan` |
| 12 | Replacing HOME with no crash-loop escape | `App` + `CrashGuard`: 3 crashes in 60 s -> next start clears default and opens the PTCL launcher |
| 13 | Math game: pressing OK repeatedly during "Great job" scored multiple times | Input locked until next question |

Not changed on purpose: UI/design, tiles, Kids PIN, EPG, presets, buffer sizes (already tuned for 1 GB).

## Final audit additions
| # | Problem | Fix |
|---|---|---|
| 14 | `app/build.gradle`: `signingConfig (cond) ? a : b` parses as a method call in Groovy and fails the build | `signingConfig = (cond) ? a : b` |
| 15 | My crash-loop escape finished HomeActivity before the UI existed; `onStart` then hit a null `wifiTv` | Null guards in `onStart` / `onNewIntent` |
| 16 | Zebra emoji (Unicode 10) renders as an empty box on Android 7.1.2 | "Z is for Zoo" with a monkey emoji (Unicode 6) |
| 17 | Gradle 8.4 does not officially support a JDK 21 runtime | Wrapper + CI use Gradle 8.5 (works with AGP 8.1.4, JDK 17 or 21) |

## Update 3 (requested after first install on the box)
- **Look:** gradient "aurora" wallpapers (5 themes; Menu > Change wallpaper), big iOS-style clock/date, glass gradient tiles.
- **Apps not installed are hidden** (Tapmad, Tamasha, VLC, Prime Video, AirPlay, ...). They appear automatically when installed.
- **Live TV on power-up:** the splash plays, then Live TV starts by itself (last watched channel, else the first). BACK, or the remote's apps button, returns to the home screen. Menu > "Start with Live TV..." turns this off.
- **IPTV player redesign:** channel logos (coloured initial if no logo), channel numbers, now-playing banner with logo, rounded glass panel, type a channel number with 0-9, dead channels are skipped automatically.
- **Files:** new built-in file manager (internal + USB/SD, open, delete, .m3u plays in Live TV, APK install).
- **Kids Zone:** rebuilt with big colourful cards. No PIN, no server/link to choose: Cartoons, Learn, Movies and Islamic use built-in playlists automatically. Parent settings removed.
- **YouTube No Ads:** tile installs/launches SmartTube (free, open source, no sign-in, ad-blocking) - downloaded from its GitHub releases on first tap.

## Update 4
- **Own branding:** splash now shows an original logo + "Rummish Saleem TV" (no Apple name/logo anywhere).
- **More IPTV sources:** "Free-TV (open source)" playlist and "Urdu channels" (iptv-org language list) added to the sources menu.
- **App Store:** new tile. Downloads SmartTube, VLC, Kodi, TV Bro, NewPipe, LocalSend straight from their official release pages with a progress bar, then runs Android's installer. Shows OPEN when already installed.
- **iOS-style Settings:** the Settings tile now opens our own app (sections on the left, rounded grouped cards, iOS switches, wallpaper picker with previews). Network, display, sound, date, language, apps, storage, developer options and "All Android settings" open the box's original screens from there. Also holds Live TV, launcher-recovery and About (model, Android, RAM, storage, IP).

## Update 5 - live wallpaper + home widgets
- **Live wallpaper:** the aurora background now drifts slowly (about 20 frames/s, shaders built once, nothing allocated per frame). It only animates while the home screen is visible, so it costs nothing during video. Switch: Settings > Display & Sound > Home screen, or Menu on the home screen. Kids Zone and the Settings previews stay static.
- **Home widgets** (a strip under the clock): *Continue watching* (shows the last channel; OK resumes Live TV), *Weather* (open-meteo.com, no key or account, refreshed at most every 30 min, last value is kept offline) and *This box* (free storage and memory). Switch and *Weather city* (looked up by name) are in Settings > Display & Sound > Home screen.
- Files changed: `WallpaperView`, `HomeActivity`, `SettingsActivity`, `IptvActivity` (remembers the last channel name). New permissions: none.
- **Not included - real Android app widgets (AppWidgetHost):** third-party widgets need the box firmware to ship a widget picker/providers, which TV builds usually lack, and a launcher cannot bind them without a system-level approval screen. The built-in widgets above work on any box. Not compiled or run here - same status as the rest of the project.

## Update 6 - "Digital TV" tile + choose what opens at power-up
- **Digital TV tile:** the streaming app `com.fun.faisal` (its own label is "Faisal") is detected like every other app and shown as **Digital TV** with its real icon, right after Live TV. Hidden until the app is installed. The app itself is *not* modified, repackaged or re-signed - only the launcher's label is ours.
- **On power-up open:** Live TV (default) / Digital TV / Home screen only. Set in Settings > Launcher > "When the TV turns on", or from the home Menu (OK cycles). If Digital TV is chosen but not installed, Live TV opens instead. BACK or HOME inside the app returns to this launcher.
- Files changed: `AppEntry`, `HomeActivity`, `SettingsActivity`, `Prefs`.
- **Limits (they are inside the other app, not the launcher):**
  - Its Splash/Main/Detail screens are locked to *portrait* by its own manifest; only its three player screens (`TvChannels`, `LiveChannel`, `liveStraming`) are landscape. A launcher cannot override another app's orientation.
  - Its home/menu screens are touch UIs. Only the full-screen live player reads the remote (Left/Right = previous/next channel, Down = controls).
  - It cannot be told to "play channel 1": the player screens read their channel list from memory that only the app's own start-up flow fills, so opening them directly would crash or show nothing.
  - It requires internet at start-up (it shows "Kindly connect your internet" otherwise), so on a cold boot the Wi-Fi may need a few seconds.
