# Troubleshooting

## Nothing shows up on screen

1. Check that Auto Shazam is on: the menu shows its switch, and Shazam shows
   a notification while it listens.
2. Check that Roomtone has notification access. Without it, Roomtone can't
   read what Shazam recognizes. Open Settings in Roomtone: the Permissions
   section shows what's missing.
3. Try playing the track a little louder or moving the phone closer to the
   speakers. If Shazam itself doesn't recognize the track, Roomtone can't
   either.

## Auto Shazam doesn't start

- Roomtone needs the "display over other apps" permission to start Shazam.
- Make sure Shazam is installed and up to date.
- Shazam comes to the front for a moment when it starts, then Roomtone
  returns. If Shazam stays in front, go back to Roomtone: it will try again.

## Roomtone stops working after a while

Many phone makers close apps in the background to save battery, and that
also disconnects notification access. If Roomtone stops seeing tracks after
the screen has been off, or after a day or two:

- set Roomtone and Shazam to "unrestricted" or "don't optimize" in the
  battery settings
- allow both to start automatically and to run in the background
- lock Roomtone in the recent apps list, if your phone supports it

[Don't kill my app!](https://dontkillmyapp.com) has step-by-step instructions
for each brand:
[Huawei](https://dontkillmyapp.com/huawei) ·
[Xiaomi](https://dontkillmyapp.com/xiaomi) ·
[Samsung](https://dontkillmyapp.com/samsung) ·
[OnePlus](https://dontkillmyapp.com/oneplus) ·
[Oppo](https://dontkillmyapp.com/oppo) ·
[Realme](https://dontkillmyapp.com/realme) ·
[Vivo](https://dontkillmyapp.com/vivo) ·
[Motorola](https://dontkillmyapp.com/motorola) ·
[Sony](https://dontkillmyapp.com/sony) ·
[Nokia](https://dontkillmyapp.com/nokia) ·
[Asus](https://dontkillmyapp.com/asus) ·
[Google](https://dontkillmyapp.com/google)

When Roomtone comes back to the front, it reconnects notification access by
itself if the system dropped it.

## A track is scrobbled with the wrong name

- Check the Drafts tab: tracks Roomtone isn't sure about wait there.
- If a song has several versions in your Last.fm history, your pick is saved
  in Settings, under the chosen versions. You can change it there.
- In Settings you can choose whether albums and titles follow your Last.fm
  history or the record you own.

See [Scrobbling](scrobbling.md) for how Roomtone picks a name.

## Scrobbles stay waiting

The Sent tab shows why: no connection, not logged in, or a Last.fm session
that has expired. Use "Retry now" once the problem is solved. If you changed
the Last.fm API key in your own build, log out and back in.

## Reporting a problem

If none of this helps, [open an issue](https://github.com/DerefDev/roomtone/issues/new/choose).
A good report includes:

- what you expected and what happened instead
- the steps to make it happen again, if you know them
- your phone model, Android version, Roomtone version (Settings → About)
  and Shazam version
- for a wrong track or scrobble: the track as Shazam showed it and as it was
  scrobbled
- screenshots, if they help

For problems with recognition or scrobbling, the log files help a lot. They
are in `Android/data/dev.deref.roomtone/files/` on the phone's storage:
`shazam_raw.jsonl` (what Shazam reported) and `listens.jsonl` (the listens
Roomtone counted). They contain the names of the tracks you played, so look
them over before attaching them, or attach only the part around the problem.
