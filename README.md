# Roomtone

Scrobble the records you play at home.

Roomtone turns a spare Android phone into a small display next to your
turntable. When you put on a record, it shows what's playing and adds each
track you actually listened to your Last.fm profile. It works the same with
CDs and tapes.

## How it works

1. Leave the phone near your speakers. Roomtone turns on Shazam's Auto Shazam
   and keeps it running while you listen.
2. It follows what Shazam recognizes and how long each track plays, and
   decides whether it counts as a listen.
3. It looks the track up in your Discogs collection and in your Last.fm
   history, so the scrobble matches the record you own and the way the song
   already appears on your profile.
4. Listens are saved on the phone and sent to Last.fm, later if you're offline.

When something isn't clear, such as an unknown track length, a song with
several versions in your history, or a track that isn't on the record you're
playing, Roomtone keeps it as a draft for you to check instead of guessing.

## What you see

- The cover of your own pressing, with title, artist, album and the next track
- Colors taken from the cover
- A QR code to the lyrics on Genius when you swipe the cover
- A dim clock in standby when nothing is playing
- A Scrobble page with what was sent, what's waiting and the drafts to review

## Requirements

- Android 8.0 or later
- The [Shazam](https://www.shazam.com) app
- A [Last.fm](https://www.last.fm) account, and optionally a
  [Discogs](https://www.discogs.com) account with your collection
- Two permissions: notification access, to read what Shazam recognizes, and
  display over other apps, to start Auto Shazam

## Install

There is no release yet. To build it yourself:

```sh
git clone https://github.com/DerefDev/roomtone.git
cd roomtone
cp secrets.properties.example secrets.properties   # then add your API keys
./gradlew assembleDebug
```

The example file links to the pages where you can create the Last.fm, Discogs
and Genius keys. The APK ends up in `app/build/outputs/apk/debug/`.

## Status

Roomtone is in active development (v0.8) and has been tested on a single
device so far. Next up: running as the phone's launcher and starting at boot.

The interface is available in English and Italian. Translations are welcome.

## Privacy

Roomtone has no server of its own. Your data stays on the phone, apart from
the requests it makes to the services listed below: scrobbles and history to
Last.fm, your collection from Discogs, and track details, artwork and lyrics
links from the others. Login sessions are stored encrypted on the device.

## Credits & licenses

**App icon** — ["Gramophone"](https://commons.wikimedia.org/wiki/File:Gramophone_(36104)_-_The_Noun_Project.svg)
by Aarne Huttunen (The Noun Project), CC0 1.0, recolored and layered for the
adaptive icon.

**Icons** — [Material Icons](https://github.com/google/material-design-icons) by Google,
Apache License 2.0 (`arrow_back`, `fast_forward`, `menu`, `music_note`,
`power_settings_new`, `list`, `settings`). Source noted in each file under
`app/src/main/res/drawable/`.

**Libraries** — Android Jetpack (Compose, AppCompat, Core, Lifecycle, Activity,
Palette), Kotlin coroutines and ZXing (QR codes), Apache License 2.0.

**Data sources**
- [Last.fm API](https://www.last.fm/api) — scrobbling, track info, listening history.
  Subject to the [Last.fm API Terms](https://www.last.fm/api/tos).
- [Discogs API](https://www.discogs.com/developers) — your collection, release
  details, tracklists and artwork. Data provided by Discogs.
- [MusicBrainz](https://musicbrainz.org) — track durations and release data. Core
  data CC0; requests follow the [rate-limiting rules](https://musicbrainz.org/doc/MusicBrainz_API/Rate_Limiting).
- [Cover Art Archive](https://coverartarchive.org) — artwork for MusicBrainz releases.
- [Deezer API](https://developers.deezer.com) — track durations, albums, tracklists
  and artwork.
- [Genius API](https://docs.genius.com) — lyrics links shown as a QR code.
- [iTunes Search API](https://performance-partners.apple.com/search-api) — artwork.
  Artwork from all sources remains the property of its rights holders.
- [Shazam](https://www.shazam.com) — recognition, read from its notifications.

Roomtone is an independent project, not affiliated with or endorsed by Shazam,
Apple, Last.fm, Discogs or Genius.

**Reference projects** — [Pano Scrobbler](https://github.com/kawaiiDango/pano-scrobbler)
(GPL-3.0) and [Finale](https://github.com/nrubin29/finale) (BSD-3-Clause).

## License

Roomtone is free software: you can redistribute it and/or modify it under the
terms of the GNU General Public License as published by the Free Software
Foundation, either version 3 of the License, or (at your option) any later
version. See [LICENSE](LICENSE).

Copyright (C) 2026 DerefDev
