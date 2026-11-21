# Changelog

Generated from the [releases on GitHub](https://github.com/DerefDev/roomtone/releases) after each release. Edit the notes there, not here.

## [v0.8](https://github.com/DerefDev/roomtone/releases/tag/v0.8) - 2026-11-21

The first public release of Roomtone, the ambient scrobbler for your records,
CDs and tapes.

Put a spare Android phone next to your speakers: Shazam listens, Roomtone
shows what's playing and adds it to your Last.fm profile, written the way you
already have it there.

#### What it does

**Now playing**
- Shows the cover, title, artist, album and next track of what's playing,
  with colors taken from the cover
- Uses the cover of your own pressing when the record is in your Discogs
  collection, or a picture of your own
- Swipe the cover to flip it and scan a QR code for the lyrics on Genius
- Switches to a dim clock and stops listening when no music is playing

**Recognition**
- Turns Auto Shazam on and off for you, and stops it when you leave the app
- Counts only what you really listened to, and a track twice if you played
  it twice
- Recognizes the record you're playing from your Discogs collection, and
  uses its tracklist and track lengths

**Scrobbling**
- Writes each song the way it already appears in your Last.fm history, so
  plays don't get split between two entries
- Asks which version to use when a song appears in your history in several
  ways, and remembers your choice; saved choices can be changed in Settings
- Keeps listens that need a check as drafts you can fix, confirm or discard
- Saves listens on the phone and sends them when you're back online, never
  twice
- Optional "actual record" mode: artist, title and album as on the record you
  own, with cleanups such as removing "Remastered"

**Everything else**
- No ads, no account other than your own, no server of its own

#### Install

1. Download `roomtone-v0.8.apk` below and install it.
2. Install [Shazam](https://www.shazam.com) on the same phone.
3. Open Roomtone, allow notification access and display over other apps.
4. Log in to Last.fm and, if you like, to Discogs.
5. Play a record, tape or CD.

Requires Android 8.0 or later.

#### Good to know

- Roomtone has been tested on a single phone so far. If something doesn't
  work on yours, see [Troubleshooting](https://derefdev.github.io/roomtone/troubleshooting/)
  or [open an issue](https://github.com/DerefDev/roomtone/issues/new/choose).
- Some phones close apps in the background to save battery: set Roomtone and
  Shazam to "unrestricted" in the battery settings.
