// Keeps names and app labels as they are when a browser translates the page:
// wraps them in <span translate="no"> so the Markdown files stay clean.
(function () {
  const terms = [
    // names
    "Roomtone", "Auto Shazam", "Shazam", "Last.fm Pro", "Last.fm", "Discogs", "Genius",
    "MusicBrainz", "Cover Art Archive", "Deezer", "iTunes", "Crowdin",
    // app labels quoted in the docs
    "Consistent with history", "Actual record", "Retry now",
    // the word Last.fm users know
    "scrobbles", "scrobbled", "scrobbling", "scrobble",
  ].sort((a, b) => b.length - a.length)
  const esc = s => s.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")
  const pattern = "\\b(" + terms.map(esc).join("|") + ")\\b"
  const re = new RegExp(pattern, "gi")
  const has = new RegExp(pattern, "i") // senza "g": test() non deve ricordare la posizione
  const skip = "script, style, code, pre, kbd, [translate='no']"

  function mark(root) {
    const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, {
      acceptNode: n => has.test(n.nodeValue) && !n.parentElement.closest(skip)
        ? NodeFilter.FILTER_ACCEPT : NodeFilter.FILTER_REJECT,
    })
    const nodes = []
    while (walker.nextNode()) nodes.push(walker.currentNode)
    for (const node of nodes) {
      const frag = document.createDocumentFragment()
      let last = 0
      node.nodeValue.replace(re, (m, _t, i) => {
        frag.append(node.nodeValue.slice(last, i))
        const span = document.createElement("span")
        span.translate = false
        span.textContent = m
        frag.append(span)
        last = i + m.length
      })
      frag.append(node.nodeValue.slice(last))
      node.replaceWith(frag)
    }
  }

  document.addEventListener("DOMContentLoaded", () => {
    document.querySelectorAll(".md-content, .md-header__title, .md-nav, .md-tabs").forEach(mark)
  })
})()
