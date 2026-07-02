package be.duncanc.discordmodbot.moderation

private const val MAX_AUDIT_REASON_LENGTH = 512

internal fun String.toAuditReason(): String = take(MAX_AUDIT_REASON_LENGTH)
