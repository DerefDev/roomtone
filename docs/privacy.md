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
install an update you asked for). [Permissions](permissions.md) explains
each one.

## What leaves the phone

| Service | What Roomtone sends | Why |
|---|---|---|
| [Last.fm](https://www.last.fm/legal/privacy) | your scrobbles (artist, title, album, time, length); requests for your history | scrobbling, matching with your history |
| [ListenBrainz](https://metabrainz.org/privacy) | your scrobbles (artist, title, album, time, length and, when your history has them, MusicBrainz IDs); requests for your history; your token, to check it and to scrobble | scrobbling, matching with your history |
| [Discogs](https://support.discogs.com/hc/articles/360009334513) | requests for your collection, releases and artists | your records, tracklists, covers |
| [Genius](https://genius.com) | artist and title of the track | lyrics link |
| [GitHub](https://docs.github.com/site-policy/privacy-policies/github-general-privacy-statement) | a request for the latest Roomtone version: once a day only if you said yes on first start (changeable in Settings), and when you check or update; never when Obtainium or an F-Droid client installed Roomtone | update notice and download |
| [Deezer](https://www.deezer.com/legal/personal-datas), [MusicBrainz](https://metabrainz.org/privacy), [Cover Art Archive](https://coverartarchive.org), [iTunes](https://www.apple.com/legal/privacy/) | artist, title or album names | lengths, albums, covers |

These requests contain the names of the tracks you play, not your name or
your accounts. Only Last.fm, ListenBrainz and Discogs know who you are, because you log in
to them.

## What is stored on the phone

- your Last.fm and Discogs login sessions and your ListenBrainz token, encrypted with the Android Keystore
- a copy of your Last.fm and ListenBrainz history and of your Discogs collection
- recent listens, drafts and the scrobble queue
- your saved version picks and settings
- cached covers, track lengths and album lookups
- two log files of recognized tracks and listens, in the app's folder on
  shared storage, useful when reporting a problem; they are cleared every two
  days by default

Logging out of Last.fm, ListenBrainz or Discogs deletes the copy of your history or
collection. Uninstalling Roomtone deletes everything.

## Your accounts

To revoke Roomtone's access, log out in the app, or remove it from your
[Last.fm applications](https://www.last.fm/settings/applications), reset your
[ListenBrainz token](https://listenbrainz.org/settings/) and remove it from your
[Discogs applications](https://www.discogs.com/settings/applications).
