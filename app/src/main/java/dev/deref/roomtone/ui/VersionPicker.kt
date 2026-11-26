package dev.deref.roomtone.ui

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.deref.roomtone.*
import dev.deref.roomtone.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val covers = LruCache<String, Bitmap>(40) // "artista|album"; ponytail: gli insuccessi non sono in cache → ritentati
private val dayFmt = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

// Copertina dell'album della versione (Deezer); senza album o su errore resta il segnaposto.
@Composable
private fun VersionCover(v: Version) {
    val key = v.album?.let { "${v.artist}|$it" }
    var bmp by remember(key) { mutableStateOf(key?.let(covers::get)) } // produceState terrebbe il valore vecchio al cambio chiave
    LaunchedEffect(key) {
        if (key == null || bmp != null) return@LaunchedEffect
        bmp = try {
            Artwork.albumCoverUrl(v.artist, v.album!!)?.let { Artwork.preview(it) }?.also { covers.put(key, it) }
        } catch (_: Exception) { null }
    }
    val m = Modifier.size(48.dp).clip(RoundedCornerShape(6.dp))
    bmp?.let { Image(it.asImageBitmap(), null, m, contentScale = ContentScale.Crop) }
        ?: Box(m.background(Color.White.copy(alpha = 0.1f)))
}

// Elenco selezionabile (stile radio) delle versioni di un brano.
@Composable
fun VersionPicker(options: List<Version>, selected: Version?, onSelect: (Version) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { v ->
            Row(Modifier.fillMaxWidth().clickable { onSelect(v) }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RadioButton(selected?.key == v.key, { onSelect(v) })
                VersionCover(v)
                Column(Modifier.weight(1f)) {
                    Text(v.title, fontSize = 15.sp)
                    Text(listOfNotNull(v.artist, v.album).joinToString(" · "), color = TextSoft, fontSize = 13.sp)
                    if (v.plays > 0) Text(pluralStringResource(R.plurals.version_plays, v.plays, v.plays, dayFmt.format(Date(v.lastUts * 1000))), color = TextSoft, fontSize = 12.sp)
                }
            }
        }
    }
}
