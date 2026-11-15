package dev.deref.roomtone

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

// Stringhe cifrate AES/GCM con chiave nell'AndroidKeyStore. Salvato: Base64(iv + ciphertext).
class SecureStore(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("secure", Context.MODE_PRIVATE)

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        g.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return g.generateKey()
    }

    fun put(name: String, value: String) {
        val c = Cipher.getInstance(TRANSFORM).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val data = c.iv + c.doFinal(value.toByteArray(Charsets.UTF_8))
        prefs.edit().putString(name, Base64.encodeToString(data, Base64.NO_WRAP)).apply()
    }

    // null se assente o non decifrabile (chiave invalidata)
    fun get(name: String): String? = try {
        val data = Base64.decode(prefs.getString(name, null) ?: return null, Base64.NO_WRAP)
        val c = Cipher.getInstance(TRANSFORM).apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, data.copyOfRange(0, IV_LEN)))
        }
        String(c.doFinal(data, IV_LEN, data.size - IV_LEN), Charsets.UTF_8)
    } catch (_: Exception) { null }

    fun remove(name: String) { prefs.edit().remove(name).apply() }

    private companion object {
        const val ALIAS = "roomtone_main"
        const val TRANSFORM = "AES/GCM/NoPadding"
        const val IV_LEN = 12
    }
}
