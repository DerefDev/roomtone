# Translations

Roomtone is written in English and translated by its users. Every language
you add makes it usable for more people, and you don't need to know how to
code.

## Translate on Crowdin

Translations are managed on Crowdin, a website where you translate one
sentence at a time, with the English text and its context next to it.

1. Open the Roomtone project on Crowdin (link coming soon).
2. Sign in and pick your language. If it isn't listed, ask for it in an
   [issue](https://github.com/DerefDev/roomtone/issues).
3. Translate, or vote on the translations already suggested.

New translations reach the repository automatically and ship with the next
release. Your language appears in Roomtone's language menu as soon as it's
included.

## A few tips

- Keep it short: most text appears on a small screen, often from a distance.
- Keep placeholders such as `%1$d` or `%1$s` exactly as they are: the app
  puts numbers and names there.
- Some sentences have different forms for one and many ("1 scrobble",
  "3 scrobbles"). Crowdin shows a field for each form your language needs.
- Leave names of services and apps as they are: Last.fm, Discogs, Shazam,
  Auto Shazam, Genius.

## Without Crowdin

You can also open a pull request: copy
`app/src/main/res/values/strings.xml` to `values-xx/strings.xml`, where `xx`
is the language code (for example `de`, or `pt-rBR` for Brazilian Portuguese),
and translate the text between the tags. The language menu picks the new
folder up by itself.
