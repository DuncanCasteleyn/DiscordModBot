package be.duncanc.discordmodbot.logging

data class StoredMessageReference(
    val messageId: Long,
    val guildId: Long,
    val channelId: Long,
    val userId: Long,
    val content: String,
    val createdAtEpochMillis: Long
)
