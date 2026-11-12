package com.ghostchat.app

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

object Backend {
    private val url = BuildConfig.SUPABASE_URL.trim().substringBefore("/rest/v1").trimEnd('/')
    val configured = url.startsWith("https://") && BuildConfig.SUPABASE_KEY.isNotBlank()
    val client by lazy {
        createSupabaseClient(url, BuildConfig.SUPABASE_KEY.trim()) {
            install(Auth); install(Postgrest); install(Realtime); install(Storage)
        }
    }
}

@Serializable
data class Profile(
    val id: String, val tag: String,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null
) { val title get() = displayName?.takeIf { it.isNotBlank() } ?: "@$tag" }

@Serializable
data class Msg(
    val id: String,
    @SerialName("conversation_id") val conversationId: String,
    @SerialName("sender_id") val senderId: String,
    val kind: String = "text", val body: String? = null,
    @SerialName("media_url") val mediaUrl: String? = null,
    @SerialName("duration_ms") val durationMs: Int? = null,
    @SerialName("font_size") val fontSize: Float = 16f,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class NewMsg(
    val id: String,
    @SerialName("conversation_id") val conversationId: String,
    @SerialName("sender_id") val senderId: String,
    val kind: String, val body: String? = null,
    @SerialName("media_url") val mediaUrl: String? = null,
    @SerialName("duration_ms") val durationMs: Int? = null,
    @SerialName("font_size") val fontSize: Float = 16f
)

@Serializable
data class MemberRow(
    @SerialName("conversation_id") val conversationId: String,
    @SerialName("user_id") val userId: String,
    val archived: Boolean = false
)

@Serializable
data class CallRow(
    val id: String,
    @SerialName("conversation_id") val conversationId: String,
    @SerialName("caller_id") val callerId: String,
    @SerialName("callee_id") val calleeId: String,
    val video: Boolean = false, val status: String = "ringing"
)

data class ChatItem(val conversationId: String, val other: Profile, val last: Msg?, val archived: Boolean)
data class ActiveCall(val id: String, val video: Boolean, val other: Profile, val isCaller: Boolean, val status: String)
data class IncomingCall(val row: CallRow, val from: Profile)
