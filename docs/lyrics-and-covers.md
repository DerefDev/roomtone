# Lyrics and covers

## Covers

For a record you own, Roomtone looks for the cover of your exact pressing:

1. your own image, if you picked one in Settings (it always wins)
2. Cover Art Archive, for the MusicBrainz release linked to your Discogs
   release
3. Last.fm
4. Cover Art Archive, for any edition of the album
5. Deezer
6. the Discogs image

Covers of your records are downloaded when the collection syncs and kept on
the phone. In Settings you can browse the alternatives for each record or
use a picture of your own.

For other tracks the order is Last.fm, Cover Art Archive, Deezer, iTunes and
the image of the Discogs master release. Until a cover arrives, Roomtone shows
the small artwork from Shazam's notification.

The screen colors are taken from the cover.

## Artists on screen

Shazam joins artists with "&", which is also part of some band names. To
tell "Artist A & Artist B" from a duo called "North & South", Roomtone looks
the name up on Discogs, then on MusicBrainz. Duos and bands keep their "&",
other names are shown as "Artist A, Artist B feat. Guest C". A name found to
be a band is remembered for good, one that isn't for 30 days.

## Lyrics

Swipe the cover to flip it: the back shows a QR code that opens the lyrics on
Genius. Roomtone doesn't show the lyrics itself.

The search starts when you flip the cover:

1. It searches Genius for the main artist and the title without "feat." or
   "Remastered", and looks for a result with the same title.
2. If that finds nothing, it searches again with the base title: the part
   before " - ", without brackets or "ft." credits. This covers titles like
   "Song A - Acoustic Version" or "Song A (with Guest B)".
3. If that still finds nothing and you own the record, it tries again with
   the title written on the record.

A result is accepted if the artist appears, as whole words, as the main
artist or among the credited artists, and if Genius doesn't mark the lyrics
as incomplete. For generic titles like "Intro", "Outro", "Interlude" or
"Skit", Roomtone prefers the result from the album you're playing; if none
matches, it takes the first.

When nothing is found, the back of the cover says that the lyrics aren't
available. Instrumental tracks still get a link, because Genius doesn't
report them in a reliable way.

Results are remembered while the app is open.
