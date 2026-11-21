#!/bin/sh
# Builds docs/index.md (the site's home page) from README.md: drops the "Docs" section, which
# has its own tab on the site, and fixes paths for the docs folder.
# Run before building or serving the site. docs/index.md is not tracked by git.
cd "$(dirname "$0")/.." || exit 1
{
  printf -- '---\ntitle: Ambient scrobbler for vinyl records, CDs and tapes\nhide:\n  - navigation\n---\n\n'
  # ponytail: drops the Website · Docs · Download line too, the site has its own tabs
  awk '/^## /{skip = ($0 == "## Docs")} !skip && !/^<p align="center"><a href="https:\/\/derefdev/' README.md |
    sed -e 's#](docs/#](#g' -e 's#src="docs/#src="#g' \
        -e 's#](LICENSE)#](https://github.com/DerefDev/roomtone/blob/main/LICENSE)#g'
} > docs/index.md
