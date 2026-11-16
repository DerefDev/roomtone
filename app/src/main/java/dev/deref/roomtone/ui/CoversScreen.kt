package dev.deref.roomtone.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.deref.roomtone.*
import dev.deref.roomtone.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

private const val MAX_SIDE = 1200

// Ridimensiona a max 1200 px e ricodifica JPEG; null = non è un'immagine
private fun jpeg1200(b: ByteArray): ByteArray? {
    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(b, 0, b.size, o)
    if (o.outWidth <= 0) return null
    var s = 1
    while (maxOf(o.outWidth, o.outHeight) / (s * 2) >= MAX_SIDE) s *= 2
    val bmp = BitmapFactory.decodeByteArray(b, 0, b.size, BitmapFactory.Options().apply { inSampleSize = s }) ?: return null
    val k = MAX_SIDE.toFloat() / maxOf(bmp.width, bmp.height)
    val out = if (k < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * k).toInt().coerceAtLeast(1), (bmp.height * k).toInt().coerceAtLeast(1), true) else bmp
    return ByteArrayOutputStream().also { out.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
}

@Composable
fun CoversScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val list by DiscogsCollection.releases.collectAsStateWithLifecycle()
    var sel by remember { mutableStateOf<OwnedRelease?>(null) }
    var version by remember { mutableIntStateOf(0) } // cambia dopo ogni modifica → ricarica le anteprime
    val scope = rememberCoroutineScope()

    fun apply(r: OwnedRelease, bytes: ByteArray?) = scope.launch {
        val j = bytes?.let { b -> withContext(Dispatchers.IO) { jpeg1200(b) } }
        if (j != null) withContext(Dispatchers.IO) { Artwork.setOverride(r.id, j) }
        version++; sel = null
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        val r = sel
        if (uri != null && r != null) scope.launch {
            // ponytail: file illeggibile → nessuna modifica
            val b = withContext(Dispatchers.IO) { try { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } } catch (_: Exception) { null } }
            apply(r, b)
        }
    }

    Page(stringResource(R.string.covers_title), onBack) {
        if (list.isEmpty()) Text(stringResource(R.string.covers_empty), Modifier.padding(24.dp), color = TextSoft)
        LazyVerticalGrid(GridCells.Adaptive(140.dp), Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(list, key = { it.id }) { r ->
                val thumb by produceState<Bitmap?>(null, r.id, version) { value = Artwork.thumb(r.id) }
                Column(Modifier.clickable { sel = r }) {
                    Tile(thumb, Modifier.fillMaxWidth().aspectRatio(1f))
                    Text(r.title, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(r.artist, fontSize = 11.sp, color = TextSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }

    sel?.let { r ->
        val cands by produceState<List<Candidate>?>(null, r.id) { value = Artwork.candidates(r) }
        AlertDialog({ sel = null },
            title = { Text("${r.title} – ${r.artist}", maxLines = 2, overflow = TextOverflow.Ellipsis) },
            text = {
                val c = cands
                if (c == null) Text(stringResource(R.string.covers_loading))
                else LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(c) { x ->
                        val p by produceState<Bitmap?>(null, x.url) { value = Artwork.preview(x.url) }
                        Column(Modifier.width(96.dp)) {
                            // anteprima non caricabile (es. 404 su CAA) → tessera vuota non cliccabile
                            Tile(p, Modifier.size(96.dp).then(if (p != null) Modifier.clickable { scope.launch { apply(r, Artwork.bytes(x.url)) } } else Modifier))
                            Text(x.source, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            },
            confirmButton = {
                Row {
                    TextButton({ pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text(stringResource(R.string.covers_gallery)) }
                    TextButton({ Artwork.clearOverride(r.id); version++; sel = null }) { Text(stringResource(R.string.covers_auto)) }
                }
            },
            dismissButton = { TextButton({ sel = null }) { Text(stringResource(R.string.cancel)) } })
    }
}

@Composable
private fun Tile(bmp: Bitmap?, modifier: Modifier) {
    val m = modifier.clip(RoundedCornerShape(8.dp))
    if (bmp != null) Image(bmp.asImageBitmap(), null, m, contentScale = ContentScale.Crop)
    else Box(m.background(Color.White.copy(alpha = 0.06f)))
}
