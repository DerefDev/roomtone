# Roomtone

Ambient scrobbler for your records, CDs and tapes.

Roomtone turns a spare Android phone into a display for your hi-fi. It
listens through Shazam's Auto Shazam notifications, matches what is
playing against your Discogs collection and scrobbles it to Last.fm.

Status: early development (listening engine v0.3).

Design document (Italian): [docs/Roomtone_Documento_di_progetto_v1.3.txt](docs/Roomtone_Documento_di_progetto_v1.3.txt)

## Credits & licenses

**Icons** — [Material Icons](https://github.com/google/material-design-icons) by Google,
Apache License 2.0 (`arrow_back`, `fast_forward`, `menu`, `music_note`,
`power_settings_new`, `list`, `settings`). Source noted in each file under
`app/src/main/res/drawable/`.

**Libraries** — Android Jetpack (Compose, AppCompat, Core, Lifecycle, Activity,
Palette) and Kotlin coroutines, Apache License 2.0.

**Data sources**
- [Last.fm API](https://www.last.fm/api) — scrobbling, track info, listening history.
  Subject to the [Last.fm API Terms](https://www.last.fm/api/tos).
- [MusicBrainz](https://musicbrainz.org) — track durations. Core data CC0;
  requests follow the [rate-limiting rules](https://musicbrainz.org/doc/MusicBrainz_API/Rate_Limiting).
- [iTunes Search API](https://performance-partners.apple.com/search-api) — temporary
  artwork. Artwork remains the property of its rights holders.
- [Shazam](https://www.shazam.com) — recognition, read from its notifications;
  Roomtone is not affiliated with or endorsed by Shazam or Apple.

**Reference projects** (studied, no code copied) — [Pano Scrobbler](https://github.com/kawaiiDango/pano-scrobbler)
(GPL-3.0) and [Finale](https://github.com/nrubin29/finale) (BSD-3-Clause).

## License

Roomtone is free software: you can redistribute it and/or modify it under the
terms of the GNU General Public License as published by the Free Software
Foundation, either version 3 of the License, or (at your option) any later
version. See [LICENSE](LICENSE).

Copyright (C) 2026 DerefDev
