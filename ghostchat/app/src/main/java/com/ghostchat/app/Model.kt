package com.ghostchat.app

import org.json.JSONObject

enum class BubbleMode { SPLIT, ALL_LEFT, ALL_RIGHT }

data class ThemeSpec(
    val name: String, val dark: Boolean,
    val bg: Int, val surface: Int, val primary: Int, val onPrimary: Int,
    val bubbleOut: Int, val bubbleIn: Int, val text: Int
) {
    fun toJson(): JSONObject = JSONObject().put("name", name).put("dark", dark).put("bg", bg)
        .put("surface", surface).put("primary", primary).put("onPrimary", onPrimary)
        .put("bubbleOut", bubbleOut).put("bubbleIn", bubbleIn).put("text", text)

    companion object {
        fun fromJson(o: JSONObject) = ThemeSpec(
            o.getString("name"), o.getBoolean("dark"), o.getInt("bg"), o.getInt("surface"),
            o.getInt("primary"), o.getInt("onPrimary"), o.getInt("bubbleOut"),
            o.getInt("bubbleIn"), o.getInt("text")
        )
    }
}

object Presets {
    val ghostDark = ThemeSpec("Ghost Dark", true, 0xFF0E1621.toInt(), 0xFF17212B.toInt(), 0xFF5EB5F7.toInt(),
        0xFF0E1621.toInt(), 0xFF2B5278.toInt(), 0xFF182533.toInt(), 0xFFFFFFFF.toInt())
    val ghostLight = ThemeSpec("Ghost Light", false, 0xFFFFFFFF.toInt(), 0xFFF1F3F5.toInt(), 0xFF3390EC.toInt(),
        0xFFFFFFFF.toInt(), 0xFFDCEEFF.toInt(), 0xFFF1F3F5.toInt(), 0xFF111111.toInt())
    val midnight = ThemeSpec("Midnight", true, 0xFF000000.toInt(), 0xFF111111.toInt(), 0xFFB388FF.toInt(),
        0xFF000000.toInt(), 0xFF4A2F7A.toInt(), 0xFF1A1A1A.toInt(), 0xFFFFFFFF.toInt())
    val forest = ThemeSpec("Forest", true, 0xFF0F1A14.toInt(), 0xFF16251D.toInt(), 0xFF6FCF97.toInt(),
        0xFF0F1A14.toInt(), 0xFF245B3E.toInt(), 0xFF1A2B22.toInt(), 0xFFEAF5EE.toInt())
    val all = listOf(ghostDark, ghostLight, midnight, forest)
}

data class Lang(val code: String, val name: String, val map: Map<String, String>) {
    fun toJson(): JSONObject {
        val m = JSONObject(); map.forEach { (k, v) -> m.put(k, v) }
        return JSONObject().put("code", code).put("name", name).put("map", m)
    }

    companion object {
        fun fromJson(o: JSONObject): Lang {
            val m = o.getJSONObject("map")
            return Lang(o.getString("code"), o.getString("name"), m.keys().asSequence().associateWith { m.getString(it) })
        }
    }
}

/** Traduzioni: se una chiave manca nella lingua scelta si usa l'inglese. */
class Strings(private val map: Map<String, String>, private val fallback: Map<String, String>) {
    operator fun get(k: String): String = map[k] ?: fallback[k] ?: k
    fun fmt(k: String, vararg a: Any): String = String.format(get(k), *a)
}

private fun parse(raw: String): Map<String, String> = raw.trim().lines().filter { it.contains('|') }.associate {
    val i = it.indexOf('|'); it.substring(0, i) to it.substring(i + 1)
}

object Builtin {
    val langs: Map<String, Lang> = mapOf(
        "it" to Lang("it", "Italiano", parse(IT + IT2)),
        "en" to Lang("en", "English", parse(EN + EN2)),
        "es" to Lang("es", "Español", parse(ES + ES2)),
        "fr" to Lang("fr", "Français", parse(FR + FR2)),
        "de" to Lang("de", "Deutsch", parse(DE + DE2))
    )
    val keys: List<String> by lazy { langs.getValue("en").map.keys.toList() }
}

private const val IT = """
chats|Chat
archived|Archiviate
archive|Archivia
unarchive|Rimuovi dall'archivio
settings|Impostazioni
text_size|Dimensione del testo
bubbles|Posizione delle nuvolette
bubbles_split|Classica (tue a destra, altrui a sinistra)
bubbles_left|Tutte a sinistra
bubbles_right|Tutte a destra
theme|Tema
new_theme|Crea un tuo tema
language|Lingua
new_language|Crea una nuova lingua
save|Salva
name|Nome
dark_mode|Tema scuro
msg_in|Ciao! Come va?
msg_out|Tutto bene, grazie!
choose_len|Quante cifre vuoi per il PIN?
digits|%d cifre
enter_pin|Inserisci il PIN
create_pin|Crea il tuo PIN
confirm_pin|Conferma il PIN
pin_mismatch|I PIN non coincidono, riprova
wrong_pin|PIN errato
locked_for|Troppi tentativi. Riprova tra %d s
demo_note|Chat di esempio: i messaggi reali arrivano nella fase 3
no_archived|Nessuna chat archiviata
c_bg|Sfondo
c_surface|Superfici
c_primary|Colore principale
c_onprimary|Testo sul colore principale
c_out|Nuvoletta inviata
c_in|Nuvoletta ricevuta
c_text|Testo
appearance|Aspetto
languages|Lingue
welcome|Benvenuto su Ghostchat
lang_hint|Traduci le frasi qui sotto. Quelle lasciate vuote restano in inglese.
"""

private const val EN = """
chats|Chats
archived|Archived
archive|Archive
unarchive|Unarchive
settings|Settings
text_size|Text size
bubbles|Bubble position
bubbles_split|Classic (yours right, theirs left)
bubbles_left|All on the left
bubbles_right|All on the right
theme|Theme
new_theme|Create your own theme
language|Language
new_language|Create a new language
save|Save
name|Name
dark_mode|Dark theme
msg_in|Hi! How are you?
msg_out|All good, thanks!
choose_len|How many digits for your PIN?
digits|%d digits
enter_pin|Enter your PIN
create_pin|Create your PIN
confirm_pin|Confirm your PIN
pin_mismatch|PINs don't match, try again
wrong_pin|Wrong PIN
locked_for|Too many attempts. Try again in %d s
demo_note|Sample chats: real messages arrive in phase 3
no_archived|No archived chats
c_bg|Background
c_surface|Surfaces
c_primary|Primary color
c_onprimary|Text on primary
c_out|Sent bubble
c_in|Received bubble
c_text|Text
appearance|Appearance
languages|Languages
welcome|Welcome to Ghostchat
lang_hint|Translate the phrases below. Empty ones stay in English.
"""

private const val ES = """
chats|Chats
archived|Archivados
archive|Archivar
unarchive|Desarchivar
settings|Ajustes
text_size|Tamaño del texto
bubbles|Posición de los globos
bubbles_split|Clásica (los tuyos a la derecha)
bubbles_left|Todos a la izquierda
bubbles_right|Todos a la derecha
theme|Tema
new_theme|Crea tu propio tema
language|Idioma
new_language|Crear un nuevo idioma
save|Guardar
name|Nombre
dark_mode|Tema oscuro
msg_in|¡Hola! ¿Qué tal?
msg_out|¡Todo bien, gracias!
choose_len|¿Cuántos dígitos quieres para el PIN?
digits|%d dígitos
enter_pin|Introduce tu PIN
create_pin|Crea tu PIN
confirm_pin|Confirma tu PIN
pin_mismatch|Los PIN no coinciden, inténtalo de nuevo
wrong_pin|PIN incorrecto
locked_for|Demasiados intentos. Reintenta en %d s
demo_note|Chats de ejemplo: los mensajes reales llegan en la fase 3
no_archived|No hay chats archivados
c_bg|Fondo
c_surface|Superficies
c_primary|Color principal
c_onprimary|Texto sobre el color principal
c_out|Globo enviado
c_in|Globo recibido
c_text|Texto
appearance|Apariencia
languages|Idiomas
welcome|Bienvenido a Ghostchat
lang_hint|Traduce las frases de abajo. Las vacías quedan en inglés.
"""

private const val FR = """
chats|Discussions
archived|Archivées
archive|Archiver
unarchive|Désarchiver
settings|Paramètres
text_size|Taille du texte
bubbles|Position des bulles
bubbles_split|Classique (les vôtres à droite)
bubbles_left|Toutes à gauche
bubbles_right|Toutes à droite
theme|Thème
new_theme|Créez votre thème
language|Langue
new_language|Créer une nouvelle langue
save|Enregistrer
name|Nom
dark_mode|Thème sombre
msg_in|Salut ! Ça va ?
msg_out|Tout va bien, merci !
choose_len|Combien de chiffres pour votre PIN ?
digits|%d chiffres
enter_pin|Saisissez votre PIN
create_pin|Créez votre PIN
confirm_pin|Confirmez votre PIN
pin_mismatch|Les PIN ne correspondent pas, réessayez
wrong_pin|PIN incorrect
locked_for|Trop de tentatives. Réessayez dans %d s
demo_note|Discussions d'exemple : les vrais messages arrivent en phase 3
no_archived|Aucune discussion archivée
c_bg|Arrière-plan
c_surface|Surfaces
c_primary|Couleur principale
c_onprimary|Texte sur la couleur principale
c_out|Bulle envoyée
c_in|Bulle reçue
c_text|Texte
appearance|Apparence
languages|Langues
welcome|Bienvenue sur Ghostchat
lang_hint|Traduisez les phrases ci-dessous. Les vides restent en anglais.
"""

private const val DE = """
chats|Chats
archived|Archiviert
archive|Archivieren
unarchive|Aus Archiv holen
settings|Einstellungen
text_size|Textgröße
bubbles|Position der Sprechblasen
bubbles_split|Klassisch (deine rechts)
bubbles_left|Alle links
bubbles_right|Alle rechts
theme|Design
new_theme|Eigenes Design erstellen
language|Sprache
new_language|Neue Sprache erstellen
save|Speichern
name|Name
dark_mode|Dunkles Design
msg_in|Hallo! Wie geht's?
msg_out|Alles gut, danke!
choose_len|Wie viele Ziffern soll deine PIN haben?
digits|%d Ziffern
enter_pin|PIN eingeben
create_pin|PIN erstellen
confirm_pin|PIN bestätigen
pin_mismatch|PINs stimmen nicht überein, versuche es erneut
wrong_pin|Falsche PIN
locked_for|Zu viele Versuche. Erneut in %d s
demo_note|Beispiel-Chats: echte Nachrichten kommen in Phase 3
no_archived|Keine archivierten Chats
c_bg|Hintergrund
c_surface|Flächen
c_primary|Hauptfarbe
c_onprimary|Text auf Hauptfarbe
c_out|Gesendete Blase
c_in|Empfangene Blase
c_text|Text
appearance|Darstellung
languages|Sprachen
welcome|Willkommen bei Ghostchat
lang_hint|Übersetze die Sätze unten. Leere bleiben auf Englisch.
"""
