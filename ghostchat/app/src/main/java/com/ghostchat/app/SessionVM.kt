package com.ghostchat.app

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.jan.supabase.gotrue.OtpType
import io.github.jan.supabase.gotrue.SessionStatus
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.builtin.OTP
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.realtime.*
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.*
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class SessionVM(app: Application) : AndroidViewModel(app) {
    var authed by mutableStateOf<Boolean?>(null); private set
    var me by mutableStateOf<Profile?>(null); private set
    var profileLoaded by mutableStateOf(false); private set
    var loadFailed by mutableStateOf(false); private set
    var emailSent by mutableStateOf<String?>(null)
    var busy by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null)

    var chats by mutableStateOf<List<ChatItem>>(emptyList()); private set
    var openChat by mutableStateOf<ChatItem?>(null); private set
    val messages = mutableStateListOf<Msg>()
    var incoming by mutableStateOf<IncomingCall?>(null); private set
    var call by mutableStateOf<ActiveCall?>(null); private set

    private var rtJob: Job? = null
    private var channel: RealtimeChannel? = null
    private val sb get() = Backend.client

    init { if (Backend.configured) watchSession() }

    private fun watchSession() = viewModelScope.launch {
        sb.auth.sessionStatus.collect { st ->
            when (st) {
                is SessionStatus.Authenticated -> {
                    authed = true
                    val uid = st.session.user?.id
                    if (uid != null && me?.id != uid) loadProfile(uid)
                }
                is SessionStatus.NotAuthenticated -> resetAll()
                else -> {}
            }
        }
    }

    private fun resetAll() {
        authed = false; me = null; profileLoaded = false; chats = emptyList(); openChat = null
        messages.clear(); incoming = null; call = null; emailSent = null
        rtJob?.cancel(); rtJob = null
        val ch = channel; channel = null
        if (ch != null) viewModelScope.launch { runCatching { ch.unsubscribe() } }
    }

    private fun loadProfile(uid: String) = viewModelScope.launch {
        loadFailed = false
        try {
            me = sb.from("profiles").select { filter { eq("id", uid) } }.decodeSingleOrNull<Profile>()
            profileLoaded = true
            if (me != null) { refreshChats(); startRealtime(uid) }
        } catch (e: Exception) { loadFailed = true }
    }

    fun retry() { sb.auth.currentUserOrNull()?.id?.let { loadProfile(it) } }

    // ---------- Registrazione con email (codice monouso) ----------
    fun sendCode(mail: String) = viewModelScope.launch {
        busy = true; error = null
        try { sb.auth.signInWith(OTP) { email = mail }; emailSent = mail }
        catch (e: Exception) { error = e.message } finally { busy = false }
    }

    fun verify(code: String) = viewModelScope.launch {
        val mail = emailSent ?: return@launch
        busy = true; error = null
        try { sb.auth.verifyEmailOtp(type = OtpType.Email.EMAIL, email = mail, token = code) }
        catch (e: Exception) { error = e.message } finally { busy = false }
    }

    fun createProfile(tag: String, name: String, photo: Uri?) = viewModelScope.launch {
        val uid = sb.auth.currentUserOrNull()?.id ?: return@launch
        busy = true; error = null
        try {
            var url: String? = null
            if (photo != null) url = uploadAvatar(uid, photo)
            val p = Profile(uid, tag.lowercase(), name.trim().ifBlank { null }, url)
            sb.from("profiles").insert(p)
            me = p; profileLoaded = true
            refreshChats(); startRealtime(uid)
        } catch (e: Exception) {
            error = if (e.message.orEmpty().contains("duplicate", true) || e.message.orEmpty().contains("23505")) "tag_taken" else e.message
        } finally { busy = false }
    }

    fun changeAvatar(photo: Uri) = viewModelScope.launch {
        val cur = me ?: return@launch
        try {
            val url = uploadAvatar(cur.id, photo)
            sb.from("profiles").update({ set("avatar_url", url) }) { filter { eq("id", cur.id) } }
            me = cur.copy(avatarUrl = url)
        } catch (e: Exception) { error = e.message }
    }

    private suspend fun uploadAvatar(uid: String, uri: Uri): String {
        val bytes = withContext(Dispatchers.IO) { readImage(uri) }
               sb.storage.from("avatars").upload("$uid/avatar.jpg", bytes, upsert = true)
        return sb.storage.from("avatars").publicUrl("$uid/avatar.jpg") + "?v=${System.currentTimeMillis()}"
    }

    private fun readImage(uri: Uri, max: Int = 512): ByteArray {
        val cr = getApplication<Application>().contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / sample > max * 2 || bounds.outHeight / sample > max * 2) sample *= 2
        val bmp = cr.openInputStream(uri)!!.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }!!
        val scale = max.toFloat() / maxOf(bmp.width, bmp.height)
        val out = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true) else bmp
        return ByteArrayOutputStream().also { out.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
    }

    fun signOut() { viewModelScope.launch { runCatching { sb.auth.signOut() } } }

    // ---------- Chat ----------
    suspend fun refreshChats() {
        val my = me ?: return
        try {
            val mine = sb.from("members").select { filter { eq("user_id", my.id) } }.decodeList<MemberRow>()
            if (mine.isEmpty()) { chats = emptyList(); return }
            val ids = mine.map { it.conversationId }
            val others = sb.from("members").select { filter { isIn("conversation_id", ids); neq("user_id", my.id) } }.decodeList<MemberRow>()
            val profs = if (others.isEmpty()) emptyMap() else
                sb.from("profiles").select { filter { isIn("id", others.map { it.userId }) } }.decodeList<Profile>().associateBy { it.id }
            val msgs = sb.from("messages").select {
                filter { isIn("conversation_id", ids) }; order("created_at", Order.DESCENDING); limit(300)
            }.decodeList<Msg>()
            val last = msgs.groupBy { it.conversationId }.mapValues { it.value.first() }
            chats = mine.mapNotNull { m ->
                val o = others.firstOrNull { it.conversationId == m.conversationId }?.let { profs[it.userId] } ?: return@mapNotNull null
                ChatItem(m.conversationId, o, last[m.conversationId], m.archived)
            }.sortedByDescending { it.last?.createdAt ?: "" }
        } catch (e: Exception) { error = e.message }
    }

    fun startChat(tag: String, onDone: () -> Unit) = viewModelScope.launch {
        busy = true; error = null
        try {
            val cid = sb.postgrest.rpc("start_chat", buildJsonObject { put("other_tag", tag.trim().removePrefix("@").lowercase()) })
                .decodeAs<String>()
            refreshChats()
            chats.firstOrNull { it.conversationId == cid }?.let { open(it) }
            onDone()
        } catch (e: Exception) {
            val m = e.message.orEmpty()
            error = when { "user_not_found" in m -> "user_not_found"; "cannot_chat_self" in m -> "cannot_chat_self"; else -> m }
        } finally { busy = false }
    }

    fun open(c: ChatItem) = viewModelScope.launch {
        openChat = c; messages.clear()
        try {
            messages.addAll(sb.from("messages").select {
                filter { eq("conversation_id", c.conversationId) }; order("created_at", Order.ASCENDING)
            }.decodeList<Msg>())
        } catch (e: Exception) { error = e.message }
    }

    fun closeChat() { openChat = null; messages.clear() }

    fun toggleArchive(c: ChatItem) = viewModelScope.launch {
        val my = me ?: return@launch
        try {
            sb.from("members").update({ set("archived", !c.archived) }) {
                filter { eq("conversation_id", c.conversationId); eq("user_id", my.id) }
            }
            refreshChats()
        } catch (e: Exception) { error = e.message }
    }

    fun sendText(text: String, fontSize: Float) = viewModelScope.launch {
        val c = openChat ?: return@launch; val my = me ?: return@launch
        try { sb.from("messages").insert(NewMsg(UUID.randomUUID().toString(), c.conversationId, my.id, "text", text, fontSize = fontSize)) }
        catch (e: Exception) { error = e.message }
    }

    fun sendVoice(file: File, durationMs: Int) = viewModelScope.launch {
        val c = openChat ?: return@launch; val my = me ?: return@launch
        try {
            val id = UUID.randomUUID().toString()
            val bytes = withContext(Dispatchers.IO) { file.readBytes() }
            sb.storage.from("voice").upload("${my.id}/$id.m4a", bytes)
            val url = sb.storage.from("voice").publicUrl("${my.id}/$id.m4a")
            sb.from("messages").insert(NewMsg(id, c.conversationId, my.id, "voice", mediaUrl = url, durationMs = durationMs))
        } catch (e: Exception) { error = e.message } finally { file.delete() }
    }

    // ---------- Realtime ----------
    private fun startRealtime(uid: String) {
        if (rtJob != null) return
        rtJob = viewModelScope.launch {
            val ch = sb.channel("ghost-$uid"); channel = ch
            val ins = ch.postgresChangeFlow<PostgresAction.Insert>(schema = "public") { table = "messages" }
            val callIns = ch.postgresChangeFlow<PostgresAction.Insert>(schema = "public") { table = "calls" }
            val callUpd = ch.postgresChangeFlow<PostgresAction.Update>(schema = "public") { table = "calls" }
            ch.subscribe()
            launch { ins.collect { onMsg(it.decodeRecord<Msg>()) } }
            launch { callIns.collect { onCallInsert(it.decodeRecord<CallRow>(), uid) } }
            launch { callUpd.collect { onCallUpdate(it.decodeRecord<CallRow>()) } }
        }
    }

    private suspend fun onMsg(m: Msg) {
        if (openChat?.conversationId == m.conversationId && messages.none { it.id == m.id }) messages.add(m)
        refreshChats()
    }

    private suspend fun onCallInsert(r: CallRow, uid: String) {
        if (r.calleeId != uid || r.status != "ringing" || call != null) return
        val p = runCatching { sb.from("profiles").select { filter { eq("id", r.callerId) } }.decodeSingle<Profile>() }.getOrNull() ?: return
        incoming = IncomingCall(r, p)
    }

    private fun onCallUpdate(r: CallRow) {
        val c = call
        if (c != null && c.id == r.id) {
            if (r.status in listOf("ended", "declined", "missed")) call = null else call = c.copy(status = r.status)
        }
        if (incoming?.row?.id == r.id && r.status != "ringing") incoming = null
    }

    // ---------- Chiamate ----------
    private fun setCallStatus(id: String, status: String) = viewModelScope.launch {
        runCatching { sb.from("calls").update({ set("status", status) }) { filter { eq("id", id) } } }
    }

    fun startCall(video: Boolean) = viewModelScope.launch {
        val c = openChat ?: return@launch; val my = me ?: return@launch
        if (call != null) return@launch
        val id = UUID.randomUUID().toString()
        try {
            sb.from("calls").insert(CallRow(id, c.conversationId, my.id, c.other.id, video, "ringing"))
            call = ActiveCall(id, video, c.other, true, "ringing")
            delay(45_000)
            if (call?.id == id && call?.status == "ringing") { setCallStatus(id, "missed"); call = null }
        } catch (e: Exception) { error = e.message }
    }

    fun accept() {
        val inc = incoming ?: return
        incoming = null
        call = ActiveCall(inc.row.id, inc.row.video, inc.from, false, "accepted")
        setCallStatus(inc.row.id, "accepted")
    }

    fun decline() { incoming?.let { setCallStatus(it.row.id, "declined") }; incoming = null }

    fun hangUp() { call?.let { setCallStatus(it.id, "ended") }; call = null }

    /** Ritorna (token, url del server LiveKit). */
    suspend fun livekitToken(callId: String): Pair<String, String> = withContext(Dispatchers.IO) {
        val jwt = sb.auth.currentAccessTokenOrNull() ?: error("no session")
        val c = URL("${BuildConfig.SUPABASE_URL}/functions/v1/livekit-token").openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json")
        c.setRequestProperty("Authorization", "Bearer $jwt")
        c.setRequestProperty("apikey", BuildConfig.SUPABASE_KEY)
        c.outputStream.use { it.write(JSONObject().put("callId", callId).toString().toByteArray()) }
        if (c.responseCode !in 200..299) error("token ${c.responseCode}")
        val j = JSONObject(c.inputStream.bufferedReader().readText())
        j.getString("token") to j.getString("url")
    }
}
