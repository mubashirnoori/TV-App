# Review pass - what was missing / broken, and what was changed
Only defects and gaps were touched; design and working features are unchanged.

| # | Problem found | Fix |
|---|---|---|
| 1 | `android.useAndroidX=false` but ExoPlayer 2.19.1 depends on `androidx.annotation` -> AGP fails the build | `android.useAndroidX=true` (gradle.properties). No AndroidX UI libs are used. |
| 2 | No Gradle wrapper (README says "Android Studio creates it"; CLI builds impossible) | Added `gradlew`, `gradlew.bat`, `gradle/wrapper/*` (Gradle 8.4, matches CI) |
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
