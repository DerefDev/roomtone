package dev.deref.roomtone.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.deref.roomtone.R

// voce: nome, cosa fornisce, sito
private class Credit(val name: Int, val desc: Int, val url: String)

private val LASTFM = "https://www.last.fm"
private val DISCOGS = "https://www.discogs.com"
private val DEEZER = "https://www.deezer.com"

private val groups = listOf(
    R.string.cr_group_recognition to listOf(Credit(R.string.credit_shazam, R.string.cr_d_shazam, "https://www.shazam.com")),
    R.string.cr_group_data to listOf(
        Credit(R.string.credit_lastfm, R.string.cr_d_lastfm, LASTFM),
        Credit(R.string.credit_mb, R.string.cr_d_mb, "https://musicbrainz.org"),
        Credit(R.string.credit_discogs, R.string.cr_d_discogs, DISCOGS),
        Credit(R.string.credit_deezer, R.string.cr_d_deezer, DEEZER),
        Credit(R.string.credit_genius, R.string.cr_d_genius, "https://genius.com"),
    ),
    R.string.cr_group_covers to listOf(
        Credit(R.string.credit_caa, R.string.cr_d_caa, "https://coverartarchive.org"),
        Credit(R.string.credit_discogs, R.string.cr_d_art_discogs, DISCOGS),
        Credit(R.string.credit_deezer, R.string.cr_d_art, DEEZER),
        Credit(R.string.credit_itunes, R.string.cr_d_art, "https://www.apple.com/itunes/"),
    ),
    R.string.cr_group_refs to listOf(
        Credit(R.string.cr_pano, R.string.cr_d_pano, "https://github.com/kawaiiDango/pano-scrobbler"),
        Credit(R.string.cr_finale, R.string.cr_d_finale, "https://github.com/nrubin29/finale"),
    ),
    R.string.cr_group_libs to listOf(
        Credit(R.string.cr_app_icon, R.string.cr_d_app_icon, "https://commons.wikimedia.org/wiki/File:Gramophone_(36104)_-_The_Noun_Project.svg"),
        Credit(R.string.cr_icons, R.string.cr_d_icons, "https://github.com/google/material-design-icons"),
        Credit(R.string.cr_jetpack, R.string.cr_d_jetpack, "https://developer.android.com/jetpack"),
        Credit(R.string.cr_coroutines, R.string.cr_d_coroutines, "https://github.com/Kotlin/kotlinx.coroutines"),
        Credit(R.string.credit_zxing, R.string.cr_d_zxing, "https://github.com/zxing/zxing"),
    ),
)

@Composable
fun CreditsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    Page(stringResource(R.string.credits_title), onBack) {
        LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            groups.forEach { (title, items) ->
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(title), color = LocalAccent.current, fontSize = 14.sp)
                        items.forEach { c ->
                            Column(Modifier.clickable {
                                try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(c.url))) } catch (_: Exception) { /* ponytail: nessun browser → nessuna azione */ }
                            }) {
                                Text(stringResource(c.name), color = LocalAccent.current)
                                Text(stringResource(c.desc), fontSize = 12.sp, color = TextSoft)
                            }
                        }
                    }
                }
            }
        }
    }
}
