package com.ghostchat.app

import android.app.Application
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

data class UiState(
    val loaded: Boolean = false, val hasPin: Boolean = false, val pinLen: Int = 4,
    val unlocked: Boolean = false, val theme: ThemeSpec = Presets.ghostDark,
    val customThemes: List<ThemeSpec> = emptyList(), val textSize: Float = 16f,
    val bubbleMode: BubbleMode = BubbleMode.SPLIT, val langCode: String = "en",
    val customLangs: List<Lang> = emptyList(), val strings: Strings = Strings(emptyMap(), emptyMap()),
    val lockUntil: Long = 0L
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val ds = app.ds
    private val unlocked = MutableStateFlow(false)

    val state: StateFlow<UiState> = combine(ds.data, unlocked) { p, u -> build(p, u) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, UiState())

    private fun build(p: Preferences, u: Boolean): UiState {
        val themes = runCatching {
            val a = JSONArray(p[PK.themes] ?: "[]"); (0 until a.length()).map { ThemeSpec.fromJson(a.getJSONObject(it)) }
        }.getOrDefault(emptyList())
        val theme = runCatching { ThemeSpec.fromJson(JSONObject(p[PK.theme]!!)) }.getOrDefault(Presets.ghostDark)
        val langs = runCatching {
            val a = JSONArray(p[PK.langs] ?: "[]"); (0 until a.length()).map { Lang.fromJson(a.getJSONObject(it)) }
        }.getOrDefault(emptyList())
        val dev = Locale.getDefault().language
        val code = p[PK.lang] ?: if (Builtin.langs.containsKey(dev)) dev else "en"
        val en = Builtin.langs.getValue("en")
        val lang = (Builtin.langs.values + langs).firstOrNull { it.code == code } ?: en
        return UiState(
            loaded = true, hasPin = p[PK.pinHash] != null, pinLen = p[PK.pinLen] ?: 4, unlocked = u,
            theme = theme, customThemes = themes, textSize = p[PK.textSize] ?: 16f,
            bubbleMode = runCatching { BubbleMode.valueOf(p[PK.bubbles]!!) }.getOrDefault(BubbleMode.SPLIT),
            langCode = lang.code, customLangs = langs, strings = Strings(lang.map, en.map),
            lockUntil = p[PK.lockUntil] ?: 0L
        )
    }

    private fun edit(block: (MutablePreferences) -> Unit) { viewModelScope.launch { ds.edit { block(it) } } }

    fun lock() { unlocked.value = false }

    fun setPin(pin: String) {
        viewModelScope.launch {
            val salt = PinCrypto.newSalt()
            val h = withContext(Dispatchers.Default) { PinCrypto.hash(pin, salt) }
            ds.edit { it[PK.pinHash] = h; it[PK.pinSalt] = salt; it[PK.pinLen] = pin.length; it[PK.fails] = 0; it[PK.lockUntil] = 0L }
            unlocked.value = true
        }
    }

    /** onResult(ok, lockUntilMillis). Dopo 5 errori il blocco cresce: 30 s, 60 s, 120 s... (max 1 ora). */
    fun tryUnlock(pin: String, onResult: (Boolean, Long) -> Unit) {
        viewModelScope.launch {
            val p = ds.data.first()
            val now = System.currentTimeMillis()
            val until = p[PK.lockUntil] ?: 0L
            if (now < until) { onResult(false, until); return@launch }
            val ok = withContext(Dispatchers.Default) { PinCrypto.verify(pin, p[PK.pinSalt]!!, p[PK.pinHash]!!) }
            if (ok) {
                ds.edit { it[PK.fails] = 0; it[PK.lockUntil] = 0L }
                unlocked.value = true; onResult(true, 0L)
            } else {
                val f = (p[PK.fails] ?: 0) + 1
                val lock = if (f >= 5) now + minOf(30_000L shl (f - 5).coerceAtMost(6), 3_600_000L) else 0L
                ds.edit { it[PK.fails] = f; it[PK.lockUntil] = lock }
                onResult(false, lock)
            }
        }
    }

    fun setTextSize(v: Float) { edit { it[PK.textSize] = v } }
    fun setBubbles(m: BubbleMode) { edit { it[PK.bubbles] = m.name } }
    fun selectTheme(t: ThemeSpec) { edit { it[PK.theme] = t.toJson().toString() } }
    fun selectLang(code: String) { edit { it[PK.lang] = code } }

    fun saveTheme(t: ThemeSpec) {
        val list = state.value.customThemes.filter { it.name != t.name } + t
        edit {
            it[PK.themes] = JSONArray(list.map { x -> x.toJson() }).toString()
            it[PK.theme] = t.toJson().toString()
        }
    }

    fun deleteTheme(name: String) {
        val list = state.value.customThemes.filter { it.name != name }
        edit { it[PK.themes] = JSONArray(list.map { x -> x.toJson() }).toString() }
    }

    fun saveLang(l: Lang) {
        val list = state.value.customLangs.filter { it.code != l.code } + l
        edit { it[PK.langs] = JSONArray(list.map { x -> x.toJson() }).toString(); it[PK.lang] = l.code }
    }
}
