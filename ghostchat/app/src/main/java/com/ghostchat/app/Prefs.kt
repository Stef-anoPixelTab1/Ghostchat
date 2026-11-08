package com.ghostchat.app

import android.content.Context
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

val Context.ds: DataStore<Preferences> by preferencesDataStore("ghostchat")

object PK {
    val theme = stringPreferencesKey("theme")
    val themes = stringPreferencesKey("themes")
    val textSize = floatPreferencesKey("text_size")
    val bubbles = stringPreferencesKey("bubbles")
    val lang = stringPreferencesKey("lang")
    val langs = stringPreferencesKey("langs")
    val pinHash = stringPreferencesKey("pin_hash")
    val pinSalt = stringPreferencesKey("pin_salt")
    val pinLen = intPreferencesKey("pin_len")
    val fails = intPreferencesKey("fails")
    val lockUntil = longPreferencesKey("lock_until")
}

/** Il PIN non viene mai salvato: solo hash PBKDF2 con salt casuale. */
object PinCrypto {
    fun newSalt(): String {
        val b = ByteArray(16); SecureRandom().nextBytes(b)
        return Base64.encodeToString(b, Base64.NO_WRAP)
    }

    fun hash(pin: String, salt: String): String {
        val spec = PBEKeySpec(pin.toCharArray(), Base64.decode(salt, Base64.NO_WRAP), 120_000, 256)
        val h = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return Base64.encodeToString(h, Base64.NO_WRAP)
    }

    fun verify(pin: String, salt: String, expected: String): Boolean =
        MessageDigest.isEqual(hash(pin, salt).toByteArray(), expected.toByteArray())
}
