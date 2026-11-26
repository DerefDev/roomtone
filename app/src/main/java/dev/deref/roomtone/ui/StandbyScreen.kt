package dev.deref.roomtone.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.deref.roomtone.EventStore
import dev.deref.roomtone.R
import dev.deref.roomtone.Services
import dev.deref.roomtone.Status
import java.util.Calendar

private tailrec fun Context.activity(): Activity? = this as? Activity ?: (this as? ContextWrapper)?.baseContext?.activity()

@Composable
fun StandbyScreen(onExit: () -> Unit) {
    val accent = LocalAccent.current
    val ctx = LocalContext.current
    // luminosità ridotta, ripristinata all'uscita
    DisposableEffect(Unit) {
        val w = ctx.activity()?.window
        w?.attributes = w?.attributes?.apply { screenBrightness = 0.25f }
        onDispose { w?.attributes = w?.attributes?.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE } }
    }
    val list by EventStore.listens.collectAsStateWithLifecycle()
    val midnight = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
    val active = Services.all.filter { it.active }.map { it.service }
    val shown = Services.visible()
    val today = list.count { x -> x.listen.start >= midnight && active.any { s -> x.of(s)?.eval?.status.let { it == Status.VALID || it == Status.DRAFT } } }
    val review = list.sumOf { x -> shown.count { s -> x.of(s)?.eval?.status == Status.DRAFT } }
    Box(
        Modifier.fillMaxSize().background(Color.Black).clickable(remember { MutableInteractionSource() }, null, onClick = onExit),
        contentAlignment = Alignment.Center,
    ) {
        Column(pixelShift(20, 15), horizontalAlignment = Alignment.CenterHorizontally) {
            Clock(Modifier, 96.sp, accent.copy(alpha = 0.6f))
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.standby_today, today), color = TextMain.copy(alpha = 0.4f), fontSize = 18.sp)
            Text(stringResource(R.string.standby_review, review), color = TextMain.copy(alpha = 0.4f), fontSize = 18.sp)
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.standby_hint), color = TextMain.copy(alpha = 0.25f), fontSize = 12.sp)
        }
    }
}
