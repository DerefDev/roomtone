# Recognition and listens

Roomtone doesn't listen to the music itself. It relies on Shazam's Auto
Shazam, which keeps listening in the background, and reads what it recognizes
from its notifications. This page explains how that becomes a listen worth
scrobbling.

## Auto Shazam

When Roomtone opens, it turns Auto Shazam on. Shazam briefly comes to the
front to start, then Roomtone returns. Shazam has no way to start silently, so
this short switch is expected.

Auto Shazam is turned off:

- when the screen goes off
- when Roomtone goes to standby
- when you leave Roomtone or close it

and turned back on when the screen comes back on, when you leave standby or
when you return to the app, if it was on before. If it doesn't start, you can
switch it on from Settings.

To start Shazam from the background, Android needs the "display over other
apps" permission. To read what Shazam recognizes, Roomtone needs notification
access.

## Beats

While Auto Shazam is on, its notification updates about every 12 seconds with
the track it is hearing, for example "Song A by Artist A". Roomtone treats
each update as a beat:

- The track appears on screen at the first beat.
- Two identical updates less than 2 seconds apart count as one.
- Notifications are read in the phone's language, the same one Shazam uses,
  even if Roomtone is set to another language.

## When a listen ends

A listen is the stretch of beats for the same track. It ends when:

- another track shows up for 2 beats in a row
- more than 2 beats in a row are something else, which lets a sample or a
  quiet passage of about 25 seconds pass without splitting the track
- there are no beats for 30 seconds

The time played is counted from the first to the last beat, plus 12 seconds.
The start is set 10 seconds before the first beat, about the time Shazam
needs to recognize a song.

## Does it count?

Roomtone then compares the time played with the length of the track.

- Listens shorter than 30 seconds, and tracks shorter than 30 seconds, are
  dropped. This is Last.fm's own rule.
- **Half rule.** The number of plays is the time played divided by the track
  length, rounded. Less than half a track is dropped, unless you listened for
  more than 4 minutes.
- **Repeats.** A track played twice in a row counts twice, with the second
  play timed one track length after the first. If the time doesn't fit the
  number of plays well (off by more than 20 seconds or 15% of the track),
  the listen becomes a draft with the number of plays for you to confirm.
- **Unknown length.** If no source knows how long the track is, the listen
  becomes a draft.

Drafts wait on the Scrobble page. You can fix them, confirm them or discard
them. See [Scrobbling](scrobbling.md) for the other reasons a listen can
become a draft.

## Track length

The length is looked up in this order, and the first answer wins:

1. the tracklist of the record you own, from Discogs
2. the phone's cache of earlier lookups
3. Last.fm
4. Deezer
5. MusicBrainz, at most one request per second; if nothing is found, it tries
   again without featured artists and with only the main artist

A length that can't be found is remembered for a day. Network errors are not
remembered, so the next listen tries again.

## Next track

Under the current track Roomtone shows the one that probably comes next, taken
from the tracklist of the record you own, then the Discogs master release,
then the Deezer album. It's a hint: Roomtone has no way to know if you skip a
track.

## Standby

After a while with no music (10 minutes by default, configurable) Roomtone
switches to a dim clock and turns Auto Shazam off. Touch the screen to wake
it up. You can also enter standby from the menu.

If you leave Settings or another screen open, Roomtone goes back to the main
screen after the same time without touches, and standby starts from there.

To protect the display from burn-in, the main screen and the standby clock
move by a few pixels every minute.

In Settings > Standby you can also let the phone lock after some time in
standby (off by default), as if you pressed the power button. Press it again
to wake the phone up. Both times, before standby and before locking, can be
changed.

To lock the phone, Roomtone needs to be a device admin with one permission
only: lock the screen. Android asks for it when you turn the option on.
Turning the option off removes the permission. Without it, Roomtone just stops
keeping the screen on, and the screen turns off with your phone's screen
timeout.
