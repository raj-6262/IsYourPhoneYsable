# Is Your Phone Usable?

An Android app that scans your phone and tells you whether it's time to upgrade.

## Run it in Android Studio
1. Unzip this folder and open it with **File > Open** in Android Studio (Koala or newer).
2. Wait for Gradle sync to finish (it downloads what it needs).
3. On your phone: Settings > About phone > tap MIUI version 7 times, then enable **USB debugging** in Developer options.
4. Connect the phone with USB, pick it in the device dropdown, press **Run**.

## Get a shareable APK
**Build > Build Bundle(s) / APK(s) > Build APK(s)**. The file is at
`app/build/outputs/apk/debug/app-debug.apk`. Copy it to your phone and install it.

## How the 100 points are split
| Test | Points |
|---|---|
| Android version | 20 |
| RAM | 15 |
| Free storage | 15 |
| Processor speed (benchmark) | 15 |
| Security patch age | 10 |
| Battery health | 10 |
| Smoothness under load (dropped frames) | 10 |
| Storage write speed | 5 |

Verdict: 75+ keep, 55-74 fine for now, 35-54 upgrade soon, under 35 upgrade now.

## Tuning
All CPU thresholds are in `Thresholds` at the top of `Scanner.kt`. They are rough guesses,
so run the app on a fast phone and a slow phone and adjust them until the scores feel right.
