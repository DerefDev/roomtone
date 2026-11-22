package dev.deref.roomtone.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.deref.roomtone.BuildConfig
import dev.deref.roomtone.R
import dev.deref.roomtone.Release

private val inline = Regex("""\*\*(.+?)\*\*|\[([^\]]+)]\(([^)]+)\)""")

// Mini Markdown: titoli #, elenchi - / *, **grassetto**, [testo](url). ponytail: niente immagini/HTML/corsivo/codice → parser vero
fun markdown(md: String, link: SpanStyle = SpanStyle()): AnnotatedString = buildAnnotatedString {
    var first = true
    for (raw in md.lines()) {
        val l = raw.trim()
        if (l.isEmpty() || l.startsWith("<") || l.startsWith("![")) continue // righe vuote, HTML, immagini
        if (!first) append("\n")
        first = false
        val h = l.startsWith("#")
        val t = if (h) l.trimStart('#').trim() else if (l.startsWith("- ") || l.startsWith("* ")) "\u2022 " + l.drop(2) else l
        val start = length
        var i = 0
        for (m in inline.findAll(t)) {
            append(t.substring(i, m.range.first)); i = m.range.last + 1
            val (b, txt, url) = m.destructured
            if (url.isNotEmpty() && (url.startsWith("https://") || url.startsWith("http://"))) withLink(LinkAnnotation.Url(url, androidx.compose.ui.text.TextLinkStyles(link))) { append(txt) }
            else if (b.isNotEmpty()) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(b) }
            else append(txt)
        }
        append(t.substring(i))
        if (h) addStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 17.sp), start, length)
    }
}

@Composable
private fun Notes(md: String) = Text(markdown(md, SpanStyle(color = LocalAccent.current)), fontSize = 14.sp)

@Composable
fun UpdateDialog(r: Release, onUpdate: () -> Unit, onLater: () -> Unit) {
    AlertDialog(onLater,
        title = { Text(stringResource(R.string.update_title, r.version)) },
        text = {
            Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.update_body, BuildConfig.VERSION_NAME, r.version), Modifier.padding(bottom = 12.dp))
                Notes(r.notes)
            }
        },
        confirmButton = { TextButton(onUpdate) { Text(stringResource(R.string.update_install)) } },
        dismissButton = { TextButton(onLater) { Text(stringResource(R.string.update_later)) } })
}

@Composable
fun ChangelogDialog(r: Release, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    AlertDialog(onDismiss,
        title = { Text(stringResource(R.string.changelog_title, r.version)) },
        text = { Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) { Notes(r.notes) } },
        confirmButton = { TextButton(onDismiss) { Text(stringResource(R.string.changelog_ok)) } },
        dismissButton = {
            TextButton({ try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/DerefDev/roomtone/releases"))) } catch (_: Exception) { /* ponytail: nessun browser → nessuna azione */ } }) {
                Text(stringResource(R.string.changelog_all))
            }
        })
}
