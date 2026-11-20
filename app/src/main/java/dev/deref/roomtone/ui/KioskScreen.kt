package dev.deref.roomtone.ui

import android.graphics.Bitmap
import android.text.format.DateFormat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import dev.deref.roomtone.AlbumSource
import dev.deref.roomtone.Lyrics
import dev.deref.roomtone.LyricsState
import dev.deref.roomtone.Track
import kotlin.math.abs
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.deref.roomtone.NowPlaying
import dev.deref.roomtone.displayArtist
import dev.deref.roomtone.stripFeat
import dev.deref.roomtone.R
import java.util.Date

// np.next: brano successivo previsto dalla tracklist (indicativo)
@Composable
fun KioskScreen(
    np: NowPlaying?, cover: Bitmap?, scrobbling: Boolean, online: Boolean, drafts: Int, shazamOn: Boolean,
    onMenu: () -> Unit, onDrafts: () -> Unit,
) {
    val accent = LocalAccent.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val h = maxHeight
        val margin = h * 0.07f
        val hint = 28.dp // spazio per "scorri per il testo" sotto la copertina
        val side = minOf(h - margin * 2 - hint, maxWidth * 0.45f)
        Row(Modifier.fillMaxSize().padding(margin), verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Cover(cover, np?.track, np?.ownedTitle, np?.album?.takeIf { it.source == AlbumSource.OWNED }?.title, Modifier.size(side))
                Box(Modifier.height(hint), contentAlignment = Alignment.BottomCenter) {
                    if (np != null) Text(stringResource(R.string.lyrics_hint), color = TextSoft.copy(alpha = 0.6f), fontSize = 12.sp)
                }
            }
            Spacer(Modifier.width(margin))
            Column(Modifier.weight(1f)) {
                Text(
                    np?.ownedTitle ?: np?.track?.let { t -> stripFeat(t.title) } ?: stringResource(R.string.listening),
                    color = TextMain, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontSize = (h.value * 0.1f).sp, lineHeight = (h.value * 0.115f).sp),
                )
                np?.let {
                    val artist = remember(it.track, it.groups) { displayArtist(it.track) { n -> n in it.groups } }
                    Text(artist, color = TextMain.copy(alpha = 0.85f), fontSize = (h.value * 0.055f).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    (it.album?.title ?: it.meta?.album)?.let { a -> Text(a, color = TextSoft, fontSize = (h.value * 0.04f).sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
                np?.next?.let {
                    Spacer(Modifier.height(h * 0.03f))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.ic_fast_forward), null, tint = accent, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(it, color = TextSoft, fontSize = (h.value * 0.04f).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        // orologio in alto a destra, accanto al pulsante del menu
        Row(Modifier.align(Alignment.TopEnd).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Clock(Modifier, (h.value * 0.06f).sp, TextMain.copy(alpha = 0.8f))
            IconButton(onClick = onMenu, Modifier.alpha(0.4f).size(56.dp)) {
                Icon(painterResource(R.drawable.ic_menu), stringResource(R.string.cd_menu), tint = accent, modifier = Modifier.size(32.dp))
            }
        }
        Row(Modifier.align(Alignment.BottomEnd).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!scrobbling) StatusBadge(stringResource(R.string.badge_scrobbling_off))
            if (!shazamOn) StatusBadge(stringResource(R.string.badge_shazam_off))
            if (!online) StatusBadge(stringResource(R.string.badge_offline))
            if (drafts > 0) StatusBadge(stringResource(R.string.badge_review, drafts), onDrafts)
        }
    }
}

@Composable
private fun Cover(bmp: Bitmap?, track: Track?, ownedTitle: String?, ownedAlbum: String?, modifier: Modifier) {
    val accent = LocalAccent.current
    val shape = RoundedCornerShape(10.dp)
    // ponytail: niente shadow colorata (su Android < 9 diventa una fascia piena) → bordo tenue dell'accento
    val m = Modifier.fillMaxSize().clip(shape).border(1.dp, accent.copy(alpha = 0.25f), shape)
    var flipped by remember(track) { mutableStateOf(false) }
    val rot by animateFloatAsState(if (flipped) 180f else 0f, tween(600), label = "flip")
    val dens = LocalDensity.current
    val threshold = with(dens) { 48.dp.toPx() }
    val density = dens.density
    var lyrics by remember(track) { mutableStateOf<LyricsState>(LyricsState.Loading) }
    LaunchedEffect(track, flipped) { if (track != null && flipped && lyrics == LyricsState.Loading) lyrics = Lyrics.state(track, ownedTitle, ownedAlbum) }
    Box(
        modifier.pointerInput(track) {
            if (track == null) return@pointerInput
            var dx = 0f
            detectHorizontalDragGestures(
                onDragStart = { dx = 0f },
                onDragEnd = { if (abs(dx) > threshold) flipped = !flipped },
                onDragCancel = { dx = 0f },
            ) { _, d -> dx += d }
        }.graphicsLayer { rotationY = rot; cameraDistance = 12f * density },
    ) {
        if (rot <= 90f) {
            if (bmp != null && track != null) Image(bmp.asImageBitmap(), null, m, contentScale = ContentScale.Crop)
            else Box(m.background(Color.White.copy(alpha = 0.06f)), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_music), null, tint = accent.copy(alpha = 0.6f), modifier = Modifier.fillMaxSize(0.4f))
            }
        } else Box(m.graphicsLayer { rotationY = 180f }.background(Color(0xFF121212)), contentAlignment = Alignment.Center) {
            when (val l = lyrics) {
                LyricsState.Loading -> CircularProgressIndicator(color = accent)
                is LyricsState.Found -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    val qr = remember(l.url) { qrBitmap(l.url) }
                    Image(qr.asImageBitmap(), null, Modifier.fillMaxSize(0.7f).aspectRatio(1f).clip(RoundedCornerShape(8.dp)).background(Color.White).padding(6.dp), filterQuality = FilterQuality.None)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.lyrics_scan), color = TextSoft, fontSize = 11.sp, textAlign = TextAlign.Center)
                }
                LyricsState.None -> Text(stringResource(R.string.lyrics_unavailable), color = TextSoft, textAlign = TextAlign.Center, modifier = Modifier.padding(16.dp))
            }
        }
    }
}

// QR nero su trasparente→bianco; margin 1 modulo
private fun qrBitmap(url: String): Bitmap {
    val m = QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 1))
    val px = IntArray(m.width * m.height) { if (m[it % m.width, it / m.width]) android.graphics.Color.BLACK else android.graphics.Color.WHITE }
    return Bitmap.createBitmap(px, m.width, m.height, Bitmap.Config.ARGB_8888)
}

@Composable
fun Clock(modifier: Modifier, size: TextUnit, color: Color) {
    val ctx = LocalContext.current
    val now by rememberNow(60_000)
    Text(DateFormat.getTimeFormat(ctx).format(Date(now)), modifier, color = color, fontSize = size) // 24h/12h di sistema
}

@Composable
private fun StatusBadge(text: String, onClick: (() -> Unit)? = null) {
    val shape = RoundedCornerShape(50)
    Text(
        text, color = TextMain.copy(alpha = 0.6f), fontSize = 12.sp,
        modifier = Modifier.clip(shape).border(1.dp, LocalAccent.current.copy(alpha = 0.4f), shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}
