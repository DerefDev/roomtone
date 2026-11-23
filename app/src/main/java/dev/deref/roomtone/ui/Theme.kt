package dev.deref.roomtone.ui

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import dev.deref.roomtone.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

val DefaultAccent = Color(0xFFEDE6D6)
val DefaultBg = Color(0xFF121212)
val TextMain = Color(0xFFEEEEEE) // bianco caldo
val TextSoft = TextMain.copy(alpha = 0.65f)

val LocalAccent = compositionLocalOf { DefaultAccent }

private class Target(val bg: Color, val accent: Color)

// Accento: vibrant → lightVibrant → dominant, scartando quasi bianchi/neri e poco saturi.
private fun usable(s: Palette.Swatch?): Boolean {
    s ?: return false
    val l = ColorUtils.calculateLuminance(s.rgb)
    return l in 0.15..0.85 && s.hsl[1] >= 0.25f
}

private fun target(p: Palette?): Target {
    if (p == null) return Target(DefaultBg, DefaultAccent)
    val bg = (p.darkMutedSwatch ?: p.darkVibrantSwatch)?.rgb?.let(::Color) ?: DefaultBg
    val acc = listOf(p.vibrantSwatch, p.lightVibrantSwatch, p.dominantSwatch).firstOrNull(::usable)?.rgb?.let(::Color)
    return Target(bg, acc ?: DefaultAccent)
}

// Colori dalla copertina (Palette), animati al cambio brano; sfondo con gradiente verso un tono più scuro.
@Composable
fun RoomtoneTheme(cover: Bitmap?, content: @Composable BoxScope.() -> Unit) {
    val palette by produceState<Palette?>(null, cover) {
        value = cover?.let { withContext(Dispatchers.Default) { Palette.from(it).generate() } }
    }
    val t = target(palette)
    val spec = androidx.compose.animation.core.tween<Color>(900)
    val bg by animateColorAsState(t.bg, spec, label = "bg")
    val accent by animateColorAsState(t.accent, spec, label = "accent")
    CompositionLocalProvider(LocalAccent provides accent, LocalContentColor provides TextMain) {
        MaterialTheme(colorScheme = darkColorScheme(primary = accent, onPrimary = Color.Black, secondary = accent, surface = bg, onSurface = TextMain, onSurfaceVariant = TextSoft)) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(bg, lerp(bg, Color.Black, 0.55f)))), content = content)
        }
    }
}

// Istante corrente, aggiornato allineato al multiplo di periodMs.
@Composable
fun rememberNow(periodMs: Long): State<Long> = produceState(System.currentTimeMillis()) {
    while (true) {
        value = System.currentTimeMillis()
        delay(periodMs - value % periodMs + 20)
    }
}

// Anti burn-in: spostamento di pochi dp (entro ±rx, ±ry) che cambia ogni minuto
@Composable
fun pixelShift(rx: Int, ry: Int): Modifier {
    val m = rememberNow(60_000).value / 60_000
    return Modifier.offset(((m * 37) % (2 * rx + 1) - rx).toInt().dp, ((m * 53) % (2 * ry + 1) - ry).toInt().dp)
}

// Pagina secondaria: freccia indietro + titolo.
@Composable
fun Page(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_back), stringResource(R.string.cd_back), tint = LocalAccent.current)
            }
            Text(title, fontSize = 20.sp)
        }
        content()
    }
}
