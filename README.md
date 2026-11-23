# Purify

A free, ad-free Android app for listening to the Quran. All 114 surahs, full-surah recitations by six well-known reciters, background playback with lock-screen controls, and offline downloads.

## Features

- All 114 surahs with Arabic and English names, meaning, verse count and Meccan/Medinan label
- Search by name or number
- Six reciters: Mishary Alafasy, Abdul Basit, As-Sudais, Al-Husary, Al-Minshawi, Ash-Shuraim
- Keeps playing with the screen off, with notification and lock-screen controls
- Next/previous surah, seek bar, repeat one / repeat all, plays on to the next surah automatically
- Download any surah for offline listening, and remove it again from the same button
- No ads, no accounts, no tracking. The only permission that touches the network is `INTERNET`

## Install

1. Open the [Releases](../../releases) page on your phone.
2. Download the latest `Purify-x.y.z.apk`.
3. Open it and allow installs from your browser or file manager if Android asks. Requires Android 8.0+.

## Publishing a new version

Every push to `main` builds the APK and publishes it as a new GitHub release (v1.0.1, v1.0.2, and so on). To pick the version number yourself, push a tag instead:

```
git tag v1.1.0
git push origin v1.1.0
```

### Signing key (do this once)

Android only lets an app update in place if every version is signed with the same key. Until you add one, each build is signed with a throwaway key, and you would have to uninstall before updating.

```
keytool -genkeypair -v -keystore purify.jks -alias purify -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 purify.jks      # copy the output
```

Then add three repository secrets under Settings → Secrets and variables → Actions:

| Secret | Value |
| --- | --- |
| `KEYSTORE_BASE64` | the base64 output above |
| `KEYSTORE_PASSWORD` | the password you chose |
| `KEY_ALIAS` | `purify` |

Keep a private backup of `purify.jks` and its password. If you lose them you can't publish updates that install over existing copies.

## Build locally

Open the folder in Android Studio, or run `./gradlew :app:assembleDebug` with JDK 17 and the Android SDK installed.

## Audio

Recitations are streamed from the free [Islamic Network audio CDN](https://alquran.cloud/cdn), which allows streaming and downloading for personal and educational use. Purify is free and non-commercial. The CDN asks apps to cache audio instead of re-requesting it, which is what the offline downloads do.

## Layout

```
app/src/main/java/com/purify/app/
  MainActivity.kt        entry point
  Screens.kt             all the Compose UI
  PurifyViewModel.kt     app state, talks to the player
  PlaybackService.kt     background audio (Media3)
  Audio.kt               reciters, audio URLs, downloads
  Surah.kt               surah model and loader
app/src/main/assets/surahs.txt   the 114 surahs
```
