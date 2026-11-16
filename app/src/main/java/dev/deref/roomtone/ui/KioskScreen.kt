package dev.deref.roomtone.ui

import android.graphics.Bitmap
import android.text.format.DateFormat
import androidx.compose.foundation.Image
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
import dev.deref.roomtone.displayTitle
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
        val side = minOf(h - margin * 2, maxWidth * 0.45f)
        Row(Modifier.fillMaxSize().padding(margin), verticalAlignment = Alignment.CenterVertically) {
            Cover(cover, np != null, Modifier.size(side))
            Spacer(Modifier.width(margin))
            Column(Modifier.weight(1f)) {
                Text(
                    np?.track?.let(::displayTitle) ?: stringResource(R.string.listening),
                    color = TextMain, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontSize = (h.value * 0.1f).sp, lineHeight = (h.value * 0.115f).sp),
                )
                np?.let {
                    Text(displayArtist(it.track) { n -> n in it.groups }, color = TextMain.copy(alpha = 0.85f), fontSize = (h.value * 0.055f).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
private fun Cover(bmp: Bitmap?, playing: Boolean, modifier: Modifier) {
    val accent = LocalAccent.current
    val shape = RoundedCornerShape(10.dp)
    // ponytail: niente shadow colorata (su Android < 9 diventa una fascia piena) → bordo tenue dell'accento
    val m = modifier.clip(shape).border(1.dp, accent.copy(alpha = 0.25f), shape)
    if (bmp != null && playing) Image(bmp.asImageBitmap(), null, m, contentScale = ContentScale.Crop)
    else Box(m.background(Color.White.copy(alpha = 0.06f)), contentAlignment = Alignment.Center) {
        Icon(painterResource(R.drawable.ic_music), null, tint = accent.copy(alpha = 0.6f), modifier = Modifier.fillMaxSize(0.4f))
    }
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
