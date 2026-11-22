#!/usr/bin/env python3
"""Turns the GitHub releases into the site's "Latest release" page and into CHANGELOG.md.

  tools/releases.py page        writes docs/release.md from the latest release
  tools/releases.py changelog   writes CHANGELOG.md from all releases
  tools/releases.py fastlane    writes fastlane/.../changelogs/<versionCode>.txt from the latest release

GitHub is the source: release notes are written (or generated) on the release itself.
A token is read from GH_TOKEN / GITHUB_TOKEN or `gh auth token`, so private repos work too.
"""
import json, os, re, subprocess, sys, urllib.request

REPO = "DerefDev/roomtone"
WEB = f"https://github.com/{REPO}"
# apre Obtainium sul telefono con Roomtone già inserito
OBTAINIUM = f"https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/{WEB}"
ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")


def token():
    t = os.environ.get("GH_TOKEN") or os.environ.get("GITHUB_TOKEN")
    if t:
        return t
    try:
        return subprocess.run(["gh", "auth", "token"], capture_output=True, text=True, check=True).stdout.strip()
    except (OSError, subprocess.CalledProcessError):
        return None


def api(path):
    req = urllib.request.Request(f"https://api.github.com/repos/{REPO}/{path}",
                                 headers={"Accept": "application/vnd.github+json"})
    if t := token():
        req.add_header("Authorization", f"Bearer {t}")
    try:
        with urllib.request.urlopen(req, timeout=20) as r:
            return json.load(r)
    except urllib.error.HTTPError as e:
        if e.code == 404:
            return None  # no release yet
        raise


def links(body):
    """GitHub shows PR/issue URLs and @mentions as short links; Markdown elsewhere needs them spelled out."""
    body = re.sub(rf"(?<![(\[])https://github\.com/{REPO}/(pull|issues)/(\d+)", rf"[#\2]({WEB}/\1/\2)", body)
    body = re.sub(rf"(?<![(\[])https://github\.com/{REPO}/compare/(\S+)", rf"[\1]({WEB}/compare/\1)", body)
    body = re.sub(r"(?<![\w/\[])@([A-Za-z0-9-]+)", r"[@\1](https://github.com/\1)", body)
    return body.strip()


def notes(rel):
    # Headings one level down, so that each release fits under its own "## " heading
    return re.sub(r"^(#+) ", r"#\1 ", links(rel.get("body") or ""), flags=re.M)


def page():
    rel = api("releases/latest")
    out = ["---\nhide:\n  - navigation\n---\n", '<div class="release-head" markdown>\n',
           '![Roomtone icon](assets/icon.svg){ width="96" }\n']
    if rel:
        out += [f"# Roomtone {rel['tag_name'].lstrip('v')}\n",
                f"Released on {rel['published_at'][:10]}\n",
                f"[Install with Obtainium]({OBTAINIUM}){{ .md-button .md-button--primary }}",
                f"[Download the APK]({rel['html_url']}){{ .md-button }}",
                f"[All releases]({WEB}/releases){{ .md-button }}\n", "</div>\n", notes(rel)]
    else:
        out += ["# Roomtone\n", "The first release is on its way.\n",
                f"[All releases]({WEB}/releases){{ .md-button }}\n", "</div>\n"]
    out.append("\n---\n\nRoomtone needs Android 8.0 or later and the Shazam app. "
               "See [Getting started](index.md#getting-started).\n")
    write("docs/release.md", "\n".join(out))


def changelog():
    rels = [r for r in (api("releases?per_page=100") or []) if not r["draft"] and not r["prerelease"]]  # betas stay out
    out = ["# Changelog\n", "Generated from the [releases on GitHub]"
           f"({WEB}/releases) after each release. Edit the notes there, not here.\n"]
    for r in rels:
        out += [f"## [{r['tag_name']}]({r['html_url']}) - {r['published_at'][:10]}\n", notes(r) or "No notes.", ""]
    write("CHANGELOG.md", "\n".join(out))


def plain(md):
    """Release notes in Markdown → the short plain text that app stores show (max 500 characters)."""
    out, skip = [], False
    for line in md.splitlines():
        line = line.strip()
        if line.startswith("#"):
            title = line.lstrip("#").strip()
            skip = title.lower() == "install"  # how to install is for the GitHub page only
            if not skip:
                out.append(title + ":")
            continue
        if skip or not line:
            continue
        line = re.sub(r"\*\*(.+?)\*\*", r"\1", re.sub(r"\[([^\]]+)\]\([^)]+\)", r"\1", line))
        if line[:2] in ("- ", "* "):
            out.append("• " + line[2:])
        elif out:
            out[-1] += " " + line  # riga a capo dentro lo stesso punto
        else:
            out.append(line)
    text = "\n".join(out)
    return text if len(text) <= 500 else text[:497].rsplit(" ", 1)[0] + "…"


def fastlane():
    rel = api("releases/latest")
    if not rel:
        return
    gradle = subprocess.run(["git", "show", f"{rel['tag_name']}:app/build.gradle.kts"], cwd=ROOT,
                            capture_output=True, text=True, check=True).stdout
    code = re.search(r"versionCode\s*=\s*(\d+)", gradle).group(1)
    path = f"fastlane/metadata/android/en-US/changelogs/{code}.txt"
    if os.path.exists(os.path.join(ROOT, path)):
        return  # già scritto a mano: vince quello
    write(path, plain(rel.get("body") or ""))


def write(path, text):
    with open(os.path.join(ROOT, path), "w", encoding="utf-8") as f:
        f.write(text.rstrip() + "\n")


if __name__ == "__main__":
    {"page": page, "changelog": changelog, "fastlane": fastlane}[sys.argv[1] if len(sys.argv) > 1 else "page"]()
