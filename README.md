# Roomtone

Ambient scrobbler for your records, CDs and tapes.

Roomtone turns a spare Android phone into a display for your hi-fi. It
listens through Shazam's Auto Shazam notifications, matches what is
playing against your Discogs collection and scrobbles it to Last.fm.

Status: early development, v0.6.1 (on `staging`).

## Features

- **Listening engine** — follows Auto Shazam's ongoing notification beat by
  beat, tolerates short gaps, counts consecutive repeats and applies the
  Last.fm rules (half the track or 4 minutes, tracks over 30 s).
- **Durations** — from the owned Discogs tracklist, Last.fm, Deezer and
  MusicBrainz, cached on the device.
- **Album and metadata** — the owned Discogs release first, then your Last.fm
  history (so scrobbles group with what you already have), the Discogs
  catalog, Last.fm and Deezer.
- **Covers** — Cover Art Archive, Last.fm, Deezer, iTunes and Discogs, with a
  manual picker for the records you own.
- **Next track** — predicted from the tracklist of the owned release, the
  Discogs master or the Deezer album.
- **Kiosk display** — cover, title, artist, album, next track and clock, with
  colors from the cover; manual and automatic standby that stops Shazam.
- **Scrobble review** — listens that need a check stay as drafts you can fix,
  confirm or discard.
- **Lyrics** — swipe the cover to flip it and scan a QR code to the lyrics on Genius.
- **Accounts** — Last.fm and Discogs login; English and Italian interface.

Coming next: persistent offline scrobble queue (v0.7), Discogs as a matching
bridge (v0.8), launcher mode and boot start (v0.9).

Design document (Italian): [docs/Roomtone_Documento_di_progetto_v1.3.txt](docs/Roomtone_Documento_di_progetto_v1.3.txt)

## Credits & licenses

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
- [Shazam](https://www.shazam.com) — recognition, read from its notifications;
  Roomtone is not affiliated with or endorsed by Shazam or Apple.

**Reference projects** — [Pano Scrobbler](https://github.com/kawaiiDango/pano-scrobbler)
(GPL-3.0) and [Finale](https://github.com/nrubin29/finale) (BSD-3-Clause).

## License

Roomtone is free software: you can redistribute it and/or modify it under the
terms of the GNU General Public License as published by the Free Software
Foundation, either version 3 of the License, or (at your option) any later
version. See [LICENSE](LICENSE).

Copyright (C) 2026 DerefDev
