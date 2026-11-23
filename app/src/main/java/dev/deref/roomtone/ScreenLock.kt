package dev.deref.roomtone

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

// Amministratore del dispositivo con il solo permesso "blocca schermo": senza root è l'unico modo
// per spegnere lo schermo come il tasto di accensione.
class ScreenLockAdmin : DeviceAdminReceiver()

object ScreenLock {
    private fun dpm(ctx: Context) = ctx.getSystemService(DevicePolicyManager::class.java)
    private fun admin(ctx: Context) = ComponentName(ctx, ScreenLockAdmin::class.java)

    fun active(ctx: Context) = dpm(ctx).isAdminActive(admin(ctx))

    // false = permesso non concesso (o revocato): chi chiama ripiega sul timeout di sistema
    fun lock(ctx: Context): Boolean =
        active(ctx) && try { dpm(ctx).lockNow(); true } catch (_: SecurityException) { false }

    // schermata di sistema per concedere il permesso
    fun request(ctx: Context) = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
        .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin(ctx))
        .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, ctx.getString(R.string.screen_lock_explain))

    // opzione spenta → via anche il permesso, così l'app si disinstalla senza passaggi in più
    fun release(ctx: Context) { if (active(ctx)) dpm(ctx).removeActiveAdmin(admin(ctx)) }
}
