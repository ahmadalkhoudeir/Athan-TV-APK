# Athan TV

An Android TV app that keeps the five daily prayer times for your location. At each prayer it shows an alert on the TV, on top of whatever is playing (Netflix, YouTube, live TV), and plays the complete athan. Your show pauses for the athan and resumes when it's over.

It works fully offline. The times are calculated on the TV itself, so there's no account, no ads, and nothing is sent anywhere.

**Works on:** Amazon Fire TV Sticks running **Fire OS 6, 7 or 8** (the 2018 Fire TV Stick 4K and every Android-based stick after it), plus Google TV / Android TV boxes on Android 7.1 or newer.
**Doesn't work on:** Fire TV models running **Vega OS** (the Fire TV Stick 4K Select and newer Vega devices), because they can't install Android apps. It also won't run on the 2014–2016 sticks that are still on Fire OS 5.

---

## What it does

**At prayer time**
- Full-screen alert (or a lower-third banner) drawn over any app, with the prayer name in Arabic and English, the time, a progress bar, and the words of the athan with their meanings
- Plays the full recording from start to finish, with a separate recording for Fajr if you want one
- Asks the app you were watching to pause, then lets it resume (or, if you prefer, stops it)
- Closes the Fire TV screensaver if it's up, so the alert can be seen
- Shows the supplication after the athan (Sahih al-Bukhari 614) for a set number of seconds
- BACK or OK on the remote stops it right away
- Won't play an athan more than 10 minutes late, for example if the stick was unplugged

**Dashboard**
- Big clock, Gregorian and Hijri dates (Umm al-Qura, adjustable ±2 days)
- All six times with the next prayer highlighted and a live countdown
- Jumu'ah on Fridays, and a Suhoor/Iftar line during Ramadan
- Qibla bearing and distance to Makkah
- Press a prayer card to switch it between **Athan**, **Silent alert** and **Off**
- **Test now** and **Test in 30 s** buttons. The 30-second test lets you start a video in another app and watch the alert appear on top of it.

**Settings**
- Location: presets for New Britain, West Hartford, Hartford, New Haven, NYC, Boston, Providence, Makkah and Madinah, or any latitude/longitude
- 13 calculation methods (default ISNA, Fajr 15° / Isha 15°)
- Asr: Standard or Hanafi
- High-latitude rule
- Per-prayer adjustments of ±30 minutes, to match your masjid's timetable
- Reminder banner 5–30 minutes before each prayer
- Alert style, athan volume, supplication time, 12/24-hour clock, and an option to wake the TV

**Getting the athan onto the TV**
- **Upload from your phone:** the TV shows an address and a PIN, and you send the MP3 from your phone's browser
- A file on the TV or on a USB drive
- Any `https://` link
- A one-tap download of a freely licensed recording from Wikimedia Commons

---

## 1. Put the project on GitHub and build the APK

You don't need Android Studio, because GitHub builds the app for you.

1. Unzip `AthanTV.zip` on your computer. You'll get an `AthanTV` folder.
2. On GitHub, open your repository and choose **Add file → Upload files**.
3. Open the `AthanTV` folder, select **everything inside it** (Ctrl+A), and drag it onto the upload page. Don't upload the zip itself, because GitHub can't build from a zip. The repository's top level should show `.github`, `app`, `gradle`, `keystore`, `tools`, `build.gradle`, `gradlew`, `settings.gradle` and `README.md`.
4. Click **Commit changes**. You can delete the old `AthanTV.zip` from the repository afterwards.
5. Open the **Actions** tab. **Build Athan TV APK** starts by itself and takes about 5 minutes. It runs the prayer-time tests, then builds the app.
6. When it shows a green check, click the run, scroll to **Artifacts**, and download **AthanTV-apk**. Unzip it to get **`AthanTV.apk`**.

If `.github` didn't upload (the Actions tab says there are no workflows), use **Add file → Create new file**, type `.github/workflows/build-apk.yml` as the name, and paste in that file's contents.

## 2. Get the Fire TV Stick ready (about 2 minutes)

1. **Check the model:** go to **Settings → My Fire TV → About**. If it says Vega OS, stop here, because this device can't run Android apps.
2. **Turn on Developer options:** on the same **About** screen, highlight your device name (for example "Fire TV Stick 4K") and press **OK** on the remote **7 times**, until it says you're a developer.
3. Go back to **Settings → My Fire TV → Developer options** and turn **ADB debugging** **ON**.
4. Find the stick's IP address under **Settings → My Fire TV → About → Network** (it looks like `192.168.1.23`).

## 3. Install the app and allow it to show over other apps (from your computer)

The computer has to be on the same Wi-Fi as the Fire TV.

1. Download **SDK Platform-Tools** for your computer from Google: https://developer.android.com/tools/releases/platform-tools and unzip it (for example to `C:\platform-tools`).
2. Copy `AthanTV.apk` into that `platform-tools` folder.
3. Open a terminal in that folder. On Windows, open the folder in File Explorer, click the address bar, type `powershell`, and press Enter.
4. Run these commands. On Mac or Linux, use `./adb` instead of `.\adb`.

```powershell
.\adb connect 192.168.1.23:5555
```
A box appears on the TV asking **"Allow USB debugging?"**. Tick **Always allow from this computer** and choose **OK**. Then:

```powershell
.\adb install -r AthanTV.apk
.\adb shell appops set com.alkhoudeir.athantv SYSTEM_ALERT_WINDOW allow
.\adb shell appops get com.alkhoudeir.athantv SYSTEM_ALERT_WINDOW
```
The install command should print `Success`, and the last command should print `SYSTEM_ALERT_WINDOW: allow`.

That `appops` line gives the app the **"display over other apps"** permission. Fire TV has no menu for this, so it has to be set once over ADB. Without it, the athan still plays but nothing appears on the screen.

## 4. First run on the TV

1. On the Fire TV, open **Athan TV**. It's under **Apps** (the grid icon, then **See all**). You can move it to the front of the row from there.
2. Choose **Setup check**. "Display over other apps" and "Exact alarms" should both show ✓.
3. Choose **Athan sound → Regular athan → Upload from my phone**. On your phone, connected to the same Wi-Fi, open the address the TV shows (like `http://192.168.1.23:8080`), pick the MP3, and type the 6-digit PIN.
   - If you want a different recording for Fajr, set it under **Fajr athan**.
   - Or pick **Download a suggested free recording** for a freely licensed athan from Wikimedia Commons.
4. Choose **Test in 30 s**, press **OK**, press **Home**, and start a video in YouTube or Netflix. Within 30 seconds the athan alert should appear on top and the video should pause. Press **BACK** to end the test.
5. Optional: **Settings** in the app lets you change the location (New Britain, CT is the default), the calculation method, Asr, reminders and so on.

## 5. Turn ADB debugging back off

Once everything works, go to **Settings → My Fire TV → Developer options** and turn **ADB debugging OFF**. Leaving it on lets other devices on your network ask to control the TV. The overlay permission you granted stays in place.

To install an update later: turn ADB debugging on, download the new `AthanTV.apk` from Actions, run `.\adb connect …` and `.\adb install -r AthanTV.apk`, then turn ADB debugging off again. Your settings and athan recordings are kept.

## Installing on the TV itself instead of a stick

This works if the TV runs **Google TV / Android TV** or has **Fire TV built in**. Samsung, LG, Roku and Vizio TVs run their own systems and can't install Android apps.

**Google TV / Android TV**
1. Settings → System → About → highlight **Android TV OS build** and press OK about 7 times.
2. Settings → System → Developer options → turn on **USB debugging**.
3. On Android TV 13 or newer over Wi-Fi, also turn on **Wireless debugging** and choose **Pair device with pairing code**.
4. The IP address is under Settings → Network & Internet → your network.

**Fire TV built in**
1. Settings → **Device & Software** → About → select the TV's name about 7 times, then press Back.
2. Device & Software → Developer options → turn on **ADB debugging**.
3. The IP address is under About → Network.

**From the computer**
- Fire TV built in, or Android TV 12 and older: `.\adb connect TV-IP:5555`
- Android TV 13+ on Wi-Fi: `.\adb pair TV-IP:PAIRING-PORT` (type the code), then `.\adb connect TV-IP:PORT` using the IP:port at the top of the Wireless debugging screen.
- Then run the same `install` and `appops` commands as in step 3.

**What to expect**
- The alert shows over the TV's own apps. Whether it also covers an HDMI input, such as a streaming stick, depends on the TV, and **Test in 30 s** will show you.
- A separate device on HDMI doesn't pause for the athan. Shows you watch in the TV's own apps do pause and resume.

## Wake the TV for Fajr (optional)

To have the athan turn the TV on, turn on **Settings → Display & Sounds → HDMI CEC Device Control** on the Fire TV, turn on CEC in the TV's own settings (it may be called Anynet+, Bravia Sync, SimpLink and so on), and then turn on **Wake the TV for athan** in Athan TV's settings. This is best effort and depends on the TV.

---

## Signing

Every build is signed with the same key, `keystore/athantv-debug.p12`, so new versions install over old ones without losing your settings. It's a **debug** key and isn't secret (its password is `android`). That's fine for a personal app you install on your own TV, and the repository is private.

If you ever share the APK publicly, switch to a private key. Run `bash tools/make-keystore.sh` and add the four values it prints as repository secrets (**Settings → Secrets and variables → Actions**). You'll need to uninstall the old version once when you make that switch.

---

## Accuracy

The app works out where the sun is for each date and finds when it reaches each prayer's angle, using the same standard formulas as PrayTimes.org and the Aladhan API. Each time is then rounded to the nearest minute.

It was checked against 552 published times from the [Aladhan API](https://aladhan.com):

| Reference set | Exact match | Max difference |
|---|---|---|
| ISNA + Standard Asr, New Britain CT, Oct 2026 | 160 / 186 | 1 min |
| Muslim World League + Hanafi Asr, New Britain CT, Jun 2026 | 155 / 180 | 1 min |
| Umm al-Qura, Makkah, Mar 2026 (incl. Ramadan 120-min Isha) | 169 / 186 | 1 min |

The same data runs as unit tests on every GitHub build, along with a daylight-saving check (1 Nov 2026) and Qibla checks. A one-minute difference between calculators is normal. If your masjid's timetable differs by a minute or two, use the per-prayer adjustments.

---

## How it works

```
AlarmManager.setAlarmClock ──► AlarmReceiver ──► AthanService (foreground, mediaPlayback)
        ▲                           │                 ├─ OverlayController  (overlay window over other apps)
        │                           │                 ├─ Audio focus (transient → other apps pause & resume)
        └──── AlarmScheduler ◄──────┘                 └─ MediaPlayer → supplication → cleanup
              (always arms just the next prayer + its reminder;
               rebuilt on boot, time change, time-zone change, settings change)
```

- `core/PrayerCalc.java`: the calculator, pure Java with no Android dependencies
- `Schedule.java`: applies your settings and offsets, and finds the next prayer
- `AlarmScheduler.java`, `AlarmReceiver.java` and `BootReceiver.java`: exact alarms that re-arm themselves
- `AthanService.java`: plays the athan as a foreground service
- `OverlayController.java`: the on-screen alert
- `UploadServer.java`: the phone-upload page
- No third-party libraries. Android's java.time backport (desugaring) is used only so the app runs on Fire OS 6.

## Security notes

- The app only asks for permissions it needs: overlay, exact alarms, boot, foreground media service, wake lock, internet (for link downloads and the upload page), and read access to audio (only when you pick a file from the TV or a USB drive).
- **Phone upload page:**
  - It only runs while the *Athan sound* screen is open, and it shuts itself off after 10 idle minutes.
  - Every upload needs a random 6-digit PIN, checked in constant time, and the page closes after 10 wrong PINs.
  - Uploads are capped at 30 MB, and a file is kept only if Android can decode it as audio.
  - The page serves only itself, with a strict Content-Security-Policy.
  - It uses plain HTTP on your home network, so don't open that screen on public Wi-Fi.
- **Link downloads** must use https, including every redirect, and are size-capped and checked the same way.
- There are no analytics and no accounts. Settings stay on the device, and backup is turned off.
- Turn ADB debugging off after setup (step 5).

## Troubleshooting

| Problem | Fix |
|---|---|
| `adb devices` shows `unauthorized` | Look at the TV and accept the "Allow USB debugging?" box, then run the command again. |
| `adb connect` times out | Check that ADB debugging is ON and that the computer is on the same Wi-Fi. Re-check the IP under About → Network. |
| `INSTALL_FAILED_OLDER_SDK` | The stick is on Fire OS 5 (2014–2016 models). This app needs Fire OS 6 or newer. |
| "Unauthorized APK installation detected" | Amazon blocks sideloading on some devices it has flagged. Only Amazon support can clear this; contact them with the device serial number. |
| The athan plays but nothing appears on screen | The overlay permission is missing. Run the `appops set` command from step 3. |
| Nothing happens at prayer time | Open the app once after installing. **Setup check → Next** shows what's scheduled. |
| My show didn't pause | Pausing depends on the other app respecting Android audio focus, and most streaming apps do. You can also set **Other apps during athan → Stop**. |
| The TV doesn't turn on by itself | See *Wake the TV for Fajr* above. |
| The Hijri date is a day off | Settings → Hijri date adjustment. |
| Times differ from my masjid by a few minutes | Use the per-prayer time adjustments. |

## Credits
- Calculation-method parameters: Aladhan API `/v1/methods`
- Adhan wording: Wikipedia, "Adhan"
- After-athan supplication: Sahih al-Bukhari 614 (sunnah.com); the English line is a plain rendering of its meaning
- Fire TV OS versions by model: Amazon Developer, "Fire OS overview"
- Icon and banner: original artwork generated by `tools/make_icons.py`
