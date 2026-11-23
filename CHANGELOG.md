# Changelog

Generated from the [releases on GitHub](https://github.com/DerefDev/roomtone/releases) after each release. Edit the notes there, not here.

## [v0.9.5](https://github.com/DerefDev/roomtone/releases/tag/v0.9.5) - 2026-11-23

#### Added

- **Update collection** button in Settings, to pick up a record you just added
  on Discogs right away.
- The Discogs collection now also updates by itself every 6 hours while
  Roomtone stays open.
- To protect the display from burn-in, the main screen moves by a few pixels
  every minute, like the standby clock.

#### Install

With Obtainium, update from Obtainium. If you installed the APK yourself,
tap **Update** in Roomtone, or download `roomtone-v0.9.5.apk` below.

## [v0.9.4](https://github.com/DerefDev/roomtone/releases/tag/v0.9.4) - 2026-11-22

#### Changed

- Settings → Permissions shows whether each permission is granted, and the
  button to grant it only when it's missing. "Display over other apps" now
  shows its state too.
- The link to the guide for keeping Roomtone running sits right under the
  battery button, and links in Settings are underlined so they stand out.

#### Install

With Obtainium, update from Obtainium. If you installed the APK yourself,
tap **Update** in Roomtone, or download `roomtone-v0.9.4.apk` below.

## [v0.9.3](https://github.com/DerefDev/roomtone/releases/tag/v0.9.3) - 2026-11-22

#### New

- **Install with Obtainium.** The easiest way to install Roomtone is now
  [Obtainium](https://github.com/ImranR98/Obtainium), which installs apps
  from GitHub and keeps them up to date, and avoids the Play Protect block
  on installs from a file. See [Getting started](https://derefdev.github.io/roomtone/#getting-started).
- When Roomtone was installed by Obtainium or an F-Droid client, updates are
  left to that app: Roomtone doesn't check GitHub itself, and Settings shows
  a button to open it. After an update you still see what's new.

#### Install

With Obtainium, update from Obtainium. If you installed the APK yourself,
tap **Update** in Roomtone, or download `roomtone-v0.9.3.apk` below.

## [v0.9.2](https://github.com/DerefDev/roomtone/releases/tag/v0.9.2) - 2026-11-22

#### New

- **Permissions explained.** If Roomtone is missing notification access or
  "display over other apps", the main screen shows what each one is for and
  a button to grant it. Settings shows the same explanations, and the new
  [Permissions](https://derefdev.github.io/roomtone/permissions/) page has
  the full list.
- Settings, the Scrobble list and the other screens go back to the main
  screen after your standby time without touches, so Roomtone goes to
  standby even if you leave Settings open.
- Italian translation of everything added in 0.9 and 0.9.1.

#### Fixed

- The "new version available" notification goes away after you update.
- Right after the phone restarts, the update confirmation now appears at
  once instead of after a few minutes.

#### Install

If you have Roomtone 0.9 or later, tap **Update** in the app. Otherwise
download `roomtone-v0.9.2.apk` below; if Google Play Protect blocks it, see
[Play Protect blocks the installation](https://derefdev.github.io/roomtone/troubleshooting/#play-protect-blocks-the-installation).

## [v0.9.1](https://github.com/DerefDev/roomtone/releases/tag/v0.9.1) - 2026-11-22

#### New

- On first start Roomtone asks whether it may check GitHub once a day for a
  new version. Nothing is checked until you say yes, and you can change it
  anytime in Settings.
- Settings → Permissions links to the guide for keeping Roomtone running on
  phones that close apps in the background, with steps for each brand.

#### Install

If you have Roomtone 0.9, tap **Update**: Roomtone downloads and installs
this version for you.

If Google Play Protect blocks the installation from a file, see
[Play Protect blocks the installation](https://derefdev.github.io/roomtone/troubleshooting/#play-protect-blocks-the-installation).

## [v0.9](https://github.com/DerefDev/roomtone/releases/tag/v0.9) - 2026-11-22

#### New

- **Updates inside the app.** Roomtone checks GitHub once a day and tells
  you when a new version is out, with a notification, a message when you
  open the app and a banner in the menu. Tap Update and Roomtone downloads
  and installs it; the first time, Android asks you to allow Roomtone to
  install apps. After updating, you see what's new.
- **Check for updates** in Settings, and a switch to turn off the daily
  check.
- Roomtone can now be translated on [Crowdin](https://crowdin.com/project/roomtone):
  help bring it to your language. See [Translations](https://derefdev.github.io/roomtone/translations/)
  for how it works.
- Languages appear in the app's language menu and in the phone's language
  settings once they are at least 80% translated.

#### Changed

- The Auto Shazam switch is no longer in the menu: Standby already turns
  Shazam off and back on. If Shazam doesn't start, turn it on from Settings.
- Roomtone now asks for permission to show notifications (Android 13 and
  later), used only for update notices.

#### Install

This is the last version you need to install by hand: from now on Roomtone
updates itself. Download `roomtone-v0.9.apk` below and install it over the
version you have.

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
