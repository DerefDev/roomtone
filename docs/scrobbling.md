# Scrobbling

Once a listen counts (see [Recognition and listens](recognition.md)),
Roomtone decides how to write it: artist, title and album. The goal is that a
song played on vinyl lands on the same Last.fm entry as the times you played
it in streaming, instead of creating a second one.

## Comparing titles

Shazam, Discogs and Last.fm often write the same song differently. To compare
them, Roomtone builds a key for each title:

- lowercase, without accents or punctuation
- without "feat." credits, "Remastered", "Deluxe", "Bonus" and similar tags,
  in brackets or after a " - "
- with only the main artist

"Song A - 2025 Remaster" by "Artist A & Guest B" and "Song A (feat. Guest B)"
by "Artist A" get the same key. Live versions, remixes, intros and numbered
parts like "II" are kept apart, because they are different recordings.

When an exact match isn't possible, Roomtone also compares titles
approximately, to get past small spelling differences. Two titles never match
if their numbers differ ("Part 1" and "Part 2", "Song" and "Song II"), and
"S.I.C." and "Sic" count as the same.

## Your Last.fm history

Roomtone downloads your whole Last.fm history once, then only the new
scrobbles, at most once an hour. For each way a song is written in your
history (artist, title and album exactly as they appear) it keeps the number
of plays and the dates of the last five.

## Choosing how to write a track

For each listen Roomtone looks for the song in your history, both with the
text from Shazam and with the title from the record you own. Then:

1. If you already picked a version for this song, it uses that one.
2. If your history has only one version, it uses that one.
3. If your last five plays of the song are all the same version, it uses that
   one. This follows the way you listen now, not the way you listened years
   ago.
4. Otherwise the listen becomes a draft with a list of the versions, showing
   cover, title, artist, album and number of plays, with the most played one
   selected. Your pick is saved and applied to the other drafts of the same
   song. You can change or remove saved picks in Settings.
5. If the song isn't in your history but is on a record you own, it's written
   as on the record.
6. Otherwise it's written as Shazam reported it.

## The record you own

If you connect Discogs, Roomtone downloads your collection and uses it to
find the record you're playing:

1. **The record in play.** After a track is found on one of your records,
   Roomtone assumes you're playing that record for the next 30 minutes and
   looks there first. A track matches only if at least one of its artists,
   guests included, appears on the record.
2. **Exact match** in your collection, by title and artist.
3. **Approximate match** among the records where one of the track's artists
   appears. If two records match equally well, none is used.

If you're playing a record and Roomtone hears a track that is neither on that
record nor in your history, the listen becomes a draft: Shazam may have
misheard.

## Album

The album sent with each scrobble is, in order: the record you own, the album
of the version picked from your history, then the Discogs catalog, Last.fm and
Deezer.

In Settings you can choose between two modes:

- **Consistent with history** (default): the album and the titles follow your Last.fm
  history, so your plays stay grouped.
- **Actual record**: artist, title and album come from the record you own.
  Optional cleanups remove "Remastered", explicit tags and album edition
  names (on by default), and versions, live tags, intro and outro labels
  (off by default). They can also move featured artists into the artist field
  or keep only the main artist.

The screen always shows your own record, whatever the mode.

## Sending to Last.fm

Valid listens go into a queue saved on the phone. Drafts join the queue when
you confirm them. If scrobbling is switched off, tracks are still shown but
not queued.

- The queue is sent in batches of 50, when the phone is online and you're
  logged in.
- Each scrobble has a fixed ID, so the same listen can't enter the queue
  twice.
- If the connection drops in the middle of a send, Roomtone checks your
  Last.fm history before sending again, so nothing is scrobbled twice.
- If Last.fm is busy, Roomtone waits 1, 2, 4 and up to 30 minutes between
  attempts. If your session has expired, it stops and asks you to log in
  again.
- Scrobbles that Last.fm refuses, for example because they are older than
  two weeks, stay in the list marked as ignored, with the reason.

The Scrobble page shows what was sent, what's waiting and the drafts.
Sent and ignored scrobbles are kept for 30 days, drafts until you handle them.
