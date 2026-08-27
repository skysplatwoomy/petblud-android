package com.skysplatwoomy.petblud

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NotificationPayload(
    val packageName: String,
    val key: String,
    val postTime: Long,
    val title: CharSequence?,
    val bigText: CharSequence?,
    val text: CharSequence?,
    val subText: CharSequence?,
)

data class ChatGptNotificationStatus(
    val key: String,
    val postTime: Long,
    val title: String,
    val body: String,
)

object ChatGptNotificationParser {
    fun parse(payload: NotificationPayload): ChatGptNotificationStatus? {
        if (payload.packageName != CHATGPT_PACKAGE) return null
        var title = sanitize(payload.title)
        var body = sequenceOf(payload.bigText, payload.text, payload.subText)
            .map(::sanitize)
            .firstOrNull(String::isNotEmpty)
            .orEmpty()
        if (body == title) body = ""
        if (title.isEmpty() && body.isEmpty()) return null

        title = title.take(MAX_TITLE_CHARACTERS)
        val bodyAllowance = (MAX_TOTAL_CHARACTERS - title.length).coerceAtLeast(0)
        body = body.take(bodyAllowance)
        return ChatGptNotificationStatus(
            key = payload.key,
            postTime = payload.postTime,
            title = title,
            body = body,
        )
    }

    private fun sanitize(value: CharSequence?): String = value
        ?.toString()
        ?.map { character -> if (character.isISOControl()) ' ' else character }
        ?.joinToString(separator = "")
        ?.trim()
        ?.replace(WHITESPACE, " ")
        .orEmpty()

    const val CHATGPT_PACKAGE = "com.openai.chatgpt"
    const val MAX_TOTAL_CHARACTERS = 280
    const val MAX_TITLE_CHARACTERS = 96
    private val WHITESPACE = Regex("\\s+")
}

class ActiveChatGptNotifications {
    private val active = mutableMapOf<String, ChatGptNotificationStatus>()
    private val mutableCurrent = MutableStateFlow<ChatGptNotificationStatus?>(null)
    private var enabled = false

    val current: StateFlow<ChatGptNotificationStatus?> = mutableCurrent.asStateFlow()

    fun setEnabled(value: Boolean) {
        enabled = value
        if (!value) clear()
    }

    fun reconcile(payloads: Iterable<NotificationPayload>) {
        if (!enabled) return
        active.clear()
        payloads.mapNotNull(ChatGptNotificationParser::parse)
            .forEach { active[it.key] = it }
        publishNewest()
    }

    fun posted(payload: NotificationPayload) {
        if (!enabled) return
        val parsed = ChatGptNotificationParser.parse(payload) ?: run {
            active.remove(payload.key)
            publishNewest()
            return
        }
        active[parsed.key] = parsed
        publishNewest()
    }

    fun removed(key: String) {
        if (active.remove(key) != null) publishNewest()
    }

    fun disconnected() = clear()

    private fun clear() {
        active.clear()
        mutableCurrent.value = null
    }

    private fun publishNewest() {
        mutableCurrent.value = active.values.maxWithOrNull(
            compareBy<ChatGptNotificationStatus> { it.postTime }.thenBy { it.key },
        )
    }
}

object ChatGptNotificationBridge {
    val notifications = ActiveChatGptNotifications()
}
