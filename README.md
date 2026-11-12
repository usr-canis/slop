# Telltale

Telltale reads every piece of car data that **Android Auto** gives to apps (speed, odometer, fuel/battery, range, compass, motion sensors, location and more) and shows it on your **phone**.

It also lists exactly which data your car supports, records drives, and exports them as CSV or JSON.

> A "telltale" is the name for the little warning lights on a car's dashboard.

---

## How it works (in one minute)

Android only gives car data to an app that is running on the car screen. So Telltale has two small parts:

1. **Car screen:** one static, glanceable screen: *"Connected · Collecting car data · View on your phone"*. You open it once on the car display. You don't need to touch it while driving.
2. **Phone app:** everything you actually look at. Live dashboard, capability report, raw values, history and settings.

The two parts share data inside the app. Collection keeps running while the phone is locked.

---

## 1. Install the app (sideload)

1. On your phone, open the [latest release](https://github.com/usr-canis/slop/releases/latest).
2. Download the file named `telltale-vX.Y.Z.apk`.
3. Tap the downloaded file. If Android asks, allow your browser or Files app to **"Install unknown apps"**, then tap **Install**.
4. Open Telltale on the phone. Tap **Allow** for location, notifications and the car permissions (speed, fuel, mileage).

## 2. Automatic logging with Bluetooth (recommended, no car screen needed)

This works the same way as Hejje.

1. Open Telltale, go to **Settings → Your car**, and tap your car's Bluetooth name. The car must already be paired with the phone.
2. Android asks whether Telltale may manage that device. Tap **Allow**. This "link" lets Android wake Telltale whenever the car connects.
3. Tap **Allow all the time** for location, so logging keeps working with the phone locked.

From then on, Telltale starts logging every time the car connects over Bluetooth and stops when it disconnects. You don't need to open anything.
In this mode it logs **phone** data: GPS speed, location, heading and connect/disconnect times.
**Car-only** data (fuel, odometer, range, the car's own speed and sensors) still needs Telltale open on the car screen (next step), because Android only hands that data to apps shown there.

## 3. Let Android Auto show a sideloaded app (for car-only data)

Android Auto hides apps that didn't come from the Play Store until you turn on its developer setting. You only do this once.

1. On the phone, open **Settings** and search for **Android Auto**, then open it.
   (On most phones: *Settings → Connected devices → Connection preferences → Android Auto*.)
2. Scroll to the bottom and tap **Version** about **10 times** until it asks *"Allow development settings?"*, then tap **OK**.
3. Tap the **⋮** menu (top right), then **Developer settings**.
4. Turn on **Unknown sources**.
5. Connect to your car (USB or wireless). Telltale now appears in the car's app launcher.

**Still not on the car screen?** Telltale has this checklist under **Settings → Android Auto**:
- Make sure **Unknown sources** is really ON (developer settings can reset after Android Auto updates).
- Force-stop Android Auto (Settings → Apps → Android Auto → Force stop), then reconnect the car.
- Android Auto settings → **Customize launcher** → tick Telltale.
- Still missing? Uninstall Telltale, turn on Unknown sources **first**, then reinstall.

The APK itself is verified to contain everything Android Auto needs: the car app service, the POI category, the car-app descriptor and the minimum API level.

## 4. Use it

1. Connect the phone to the car.
2. On the **car screen**, open **Telltale** from the app launcher.
3. Look at the **phone**. The top bar shows:
   - **Not connected**: Android Auto isn't connected.
   - **Connected to Android Auto**: open Telltale on the car screen.
   - **Collecting car data**: everything is working.

| Phone tab | What it shows |
|---|---|
| **Live** | Speed (car vs phone GPS), odometer, range, fuel/battery, heading, accelerometer and gyroscope graphs, location |
| **Report** | Checklist of every field, coloured by status. Shareable as text or an image |
| **Raw** | Every value with its timestamp, status and update rate |
| **History** | Odometer, fuel and range over time, plus a session log (connect/disconnect times) |
| **Settings** | Recording, sample rate, units, data retention, export/import, delete all, update check |

**Automatic logging:** recording is **on by default**. Every time Telltale runs on the car screen, all data is logged with no button to press. You can turn it off in Settings.
If Android Auto connects but Telltale isn't open on the car screen, the phone shows a reminder after about 20 seconds: *"Open Telltale on your car screen"*.
The reminder only appears if Telltale is still running in the background, because Android doesn't wake sideloaded apps when the car connects.

**Status colours:** green = available · amber = unavailable · red = no permission · grey = unimplemented / not supported / unknown.

## 5. Update the app

In **Settings → Check for updates**, Telltale asks GitHub for the newest release, shows its notes, downloads the APK and opens Android's installer.

The first time, Android asks you to allow Telltale to *"install unknown apps"*. Allow it, then tap the button again.

---

## What data is available?

All of this is checked against the official Android for Cars App Library docs (version **1.7.0**, latest stable) and its source code.

| Data | Needs permission | Notes |
|---|---|---|
| Model: make, name, year | none | |
| Energy profile: fuel types, EV connectors | CAR_FUEL | |
| Toll card state | none | |
| Energy level: battery %, fuel %, low fuel, range, display units | CAR_FUEL | |
| Speed: raw, display, display unit | CAR_SPEED | |
| Mileage: odometer, display unit | CAR_MILEAGE | Library quirk: `getOdometerMeters()` actually returns **kilometres** |
| EV status: charge port open/connected | CAR_FUEL | Marked *experimental* by Google |
| Compass, accelerometer, gyroscope | none | Requested at the fastest allowed rate. Depends on the car maker |
| Car hardware location | fine location | |
| Exterior dimensions | n/a | **Android Automotive only.** Always "Unimplemented" on Android Auto |
| Host info: Car App API level, host package/version/UID, connection type | none | |

Many cars only report some of these. That's the point of the **Report** tab.

### Known limitations

- Wired vs wireless isn't exposed by any public Android API. The app shows *projection* vs *native* (Android Automotive) instead.
- Exterior dimensions never work on Android Auto (Google hasn't implemented them for phones yet).
- RPM, coolant temperature, fault codes and similar data **aren't available** through Android Auto at all.
- The car-side session uses an "allow all hosts" validator. That's fine for a personal sideloaded app, but it would need tightening for Play Store distribution.
- If Android refuses to start the background service (rare), data still flows while the car screen session is open, but the lock-screen notification won't appear.

### Future options (not built)

- **OBD-II Bluetooth adapter** support (ELM327 style) for RPM, coolant temperature, fault codes, fuel trims and more.

---

## Test without a car (Desktop Head Unit)

Google's **Desktop Head Unit (DHU)** shows a fake car screen on your computer and can simulate sensors.

1. Install **Android Studio**. Open **SDK Manager → SDK Tools**, tick **Android Auto Desktop Head Unit Emulator**, then click **Apply**.
2. On the phone: in Android Auto, turn on developer settings (section 2 above). Then use **⋮ → Start head unit server**.
3. Connect the phone by USB and run these in a terminal:
   ```
   adb forward tcp:5277 tcp:5277
   cd <Android SDK>/extras/google/auto
   ./desktop-head-unit -c /path/to/slop/docs/dhu/telltale-headunit.ini
   ```
   On Windows, run `desktop-head-unit.exe`. The config file turns on all simulated sensors and pretends to be a hybrid car.
4. On the DHU screen, open **Telltale**.
5. Type sensor commands into the DHU terminal, for example:
   ```
   speed 14
   fuel 60
   range 380
   odometer 48211
   compass 90 0 0
   accel 0.3 0.1 9.8
   gyro 0.01 0 0.02
   location 52.52 13.405 5 34 14 90
   tollcard insert
   ```
   Or play a short scripted drive: `./desktop-head-unit -c …/telltale-headunit.ini < /path/to/slop/docs/dhu/drive-script.txt`.
6. Watch the values change on the phone's **Live** and **Raw** tabs.

---

## For maintainers

### Build and test on a computer

You need Android Studio, or just JDK 21 and the Android SDK.
```
./gradlew assembleDebug testDebugUnitTest lintDebug
```

### CI

Every push runs the **CI** workflow: build, unit tests and lint, and it uploads a debug APK as a build artifact.

### Releasing

1. Make sure the signing secrets are set up (below).
2. Either:
   - create and push a tag such as `v1.2.0`, **or**
   - change the number in `version.txt` (e.g. `1.2.0`) and commit with `[release]` in the commit message. On GitHub you can edit the file in the browser and type for example `Bump to 1.2.0 [release]` as the commit message.

   The **Release** workflow tests the code, builds a signed APK and publishes a GitHub Release with the APK attached. The version number in the app comes from the tag or `version.txt`.

If the signing secrets are missing, the release still gets built, but it's marked **Preview** and signed with a temporary key. Android refuses to update an app whose signature has changed, so when you later switch to your real key, uninstall the preview once.

### Create the signing key (one time, about 10 minutes)

Your signing key proves future updates come from you. **Keep the file and passwords private and backed up.** If you lose them, you can't publish updates the phone will accept without reinstalling.

1. Install Java: download **Temurin JDK 21** from <https://adoptium.net> and install it.
2. Open a terminal (Windows: *PowerShell*; Mac: *Terminal*) and run:
   ```
   keytool -genkeypair -v -keystore telltale-release.jks -alias telltale -keyalg RSA -keysize 4096 -validity 10000
   ```
   It asks for a password (type it twice) and some name questions (any answers are fine). Remember the password.
3. Turn the key file into text so GitHub can store it:
   - **Mac / Linux:** `base64 -i telltale-release.jks -o telltale-release.txt`
   - **Windows (PowerShell):** `[Convert]::ToBase64String([IO.File]::ReadAllBytes("telltale-release.jks")) | Out-File telltale-release.txt -Encoding ascii`
4. On GitHub, open the repository, then **Settings → Secrets and variables → Actions → New repository secret**. Add these four (never paste them anywhere else):

   | Name | Value |
   |---|---|
   | `TELLTALE_KEYSTORE_BASE64` | the whole contents of `telltale-release.txt` |
   | `TELLTALE_KEYSTORE_PASSWORD` | the password you chose |
   | `TELLTALE_KEY_ALIAS` | `telltale` |
   | `TELLTALE_KEY_PASSWORD` | the same password |

5. Delete `telltale-release.txt`. Keep `telltale-release.jks` somewhere safe, such as a password manager or an encrypted USB stick. Never commit it; `.gitignore` already blocks `*.jks`.
6. Release a new version: on GitHub go to **Releases → Draft a new release → Choose a tag**, type for example `v0.2.0`, choose **Create new tag**, then **Publish**.
   The workflow replaces that draft with the built APK.

### Project layout

```
app/src/main/java/app/telltale/
  car/        Car App Library service, session, status screen, data collector
  data/       shared live repository, settings, Room DB, recorder, export format
  model/      field registry, status + value mapping, capability report
  phone/      phone GPS
  service/    foreground service (keeps running while the phone is locked)
  update/     GitHub release update checker
  ui/         Compose screens (Live, Report, Raw, History, Settings)
```

Main libraries: `androidx.car.app` 1.7.0 (+ `app-projected`), Jetpack Compose (BOM 2026.09.00) with Material 3, Room 2.8.5, DataStore 1.3.0, kotlinx.serialization. compileSdk/targetSdk 37, minSdk 26, AGP 9.5.1, Kotlin 2.4.21.
