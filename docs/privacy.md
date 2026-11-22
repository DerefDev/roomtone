# Privacy

Roomtone has no server of its own, no ads, no analytics and no crash
reporting. Everything it knows about you stays on the phone, apart from the
requests described below.

## What Roomtone reads on the phone

- **Shazam's notifications.** Notification access lets Roomtone see every
  notification, but it only reads the ones from Shazam and ignores the rest.
- **No microphone.** Roomtone never uses the microphone. Shazam does the
  listening, under [Shazam's own privacy policy](https://www.apple.com/legal/privacy/).

The other permissions are internet access, network state (to know when to
send queued scrobbles), display over other apps (to start Auto Shazam),
notifications (to tell you about a new version) and installing apps (to
install an update you asked for).

## What leaves the phone

| Service | What Roomtone sends | Why |
|---|---|---|
| [Last.fm](https://www.last.fm/legal/privacy) | your scrobbles (artist, title, album, time, length); requests for your history and for track and album details | scrobbling, matching with your history, covers, lengths |
| [Discogs](https://support.discogs.com/hc/articles/360009334513) | requests for your collection, releases and artists | your records, tracklists, covers |
| [Genius](https://genius.com) | artist and title of the track | lyrics link |
| [GitHub](https://docs.github.com/site-policy/privacy-policies/github-general-privacy-statement) | a request for the latest Roomtone version, once a day (can be turned off in Settings) and when you check or update | update notice and download |
| [Deezer](https://www.deezer.com/legal/personal-datas), [MusicBrainz](https://metabrainz.org/privacy), [Cover Art Archive](https://coverartarchive.org), [iTunes](https://www.apple.com/legal/privacy/) | artist, title or album names | lengths, albums, covers |

These requests contain the names of the tracks you play, not your name or
your accounts. Only Last.fm and Discogs know who you are, because you log in
to them.

## What is stored on the phone

- your Last.fm and Discogs login sessions, encrypted with the Android Keystore
- a copy of your Last.fm history and of your Discogs collection
- recent listens, drafts and the scrobble queue
- your saved version picks and settings
- cached covers, track lengths and album lookups
- two log files of recognized tracks and listens, in the app's folder on
  shared storage, useful when reporting a problem; they are cleared every two
  days by default

Logging out of Last.fm or Discogs deletes the copy of your history or
collection. Uninstalling Roomtone deletes everything.

## Your accounts

To revoke Roomtone's access, log out in the app, or remove it from your
[Last.fm applications](https://www.last.fm/settings/applications) and your
[Discogs applications](https://www.discogs.com/settings/applications).
