# Build from source

Most people should install the APK from the
[latest release](https://github.com/DerefDev/roomtone/releases/latest). Build
Roomtone yourself if you want to change it or use your own API keys.

## Requirements

- JDK 17 or later
- Android SDK (Android Studio installs it for you)
- An Android 8.0+ phone with USB debugging enabled, to install the build

## Steps

```sh
git clone https://github.com/DerefDev/roomtone.git
cd roomtone
cp secrets.properties.example secrets.properties
```

Add your API keys to `secrets.properties` (see below), then build and install:

```sh
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`secrets.properties` is ignored by git, so your keys never end up in a
commit. The keys are compiled into the app: after changing them, build again.

## API keys

| Key | Where to get it | Used for |
|---|---|---|
| `LASTFM_API_KEY`, `LASTFM_API_SECRET` | [last.fm/api/account/create](https://www.last.fm/api/account/create) | login, scrobbling, history |
| `DISCOGS_CONSUMER_KEY`, `DISCOGS_CONSUMER_SECRET` | [discogs.com/settings/developers](https://www.discogs.com/settings/developers) | your collection, releases |
| `GENIUS_ACCESS_TOKEN` | [genius.com/api-clients](https://genius.com/api-clients), "Client Access Token" | lyrics links |

> [!IMPORTANT]
> From 1 December 2026 Last.fm API keys only work while the account that owns
> them has a [Last.fm Pro](https://www.last.fm/pro) subscription. If you build
> Roomtone with your own Last.fm key, that account needs Pro. People who
> install a release don't need Pro themselves: the requirement applies to the
> owner of the key built into the app.

The Last.fm keys are needed to scrobble. The others are optional: without the
Discogs keys the collection features are off, and without the Genius token
there is no lyrics QR code.

If you change the Last.fm key on a phone that is already logged in, log out
and back in from Settings: the saved session belongs to the old key.

## Tests

```sh
./gradlew testDebugUnitTest
```
