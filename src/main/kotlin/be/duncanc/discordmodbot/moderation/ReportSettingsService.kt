package be.duncanc.discordmodbot.moderation

import be.duncanc.discordmodbot.moderation.persistence.ReportSettings
import be.duncanc.discordmodbot.moderation.persistence.ReportSettingsRepository
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class ReportSettingsService(
    private val reportSettingsRepository: ReportSettingsRepository
) {
    fun getSettings(guildId: Long): ReportSettings {
        return reportSettingsRepository.findById(guildId).orElse(ReportSettings(guildId))
    }

    fun isReportingEnabled(guildId: Long): Boolean {
        return getSettings(guildId).enabled
    }

    fun isUserBlocked(guildId: Long, userId: Long): Boolean {
        return getSettings(guildId).blockedUserIds.contains(userId)
    }

    fun getUrgentMention(guild: Guild): String {
        val roleId = getSettings(guild.idLong).urgentRoleId ?: return EVERYONE_MENTION
        return guild.getRoleById(roleId)?.asMention ?: EVERYONE_MENTION
    }

    fun getReportChannel(guild: Guild): TextChannel? {
        val channelId = getSettings(guild.idLong).reportChannelId ?: return null
        return guild.getTextChannelById(channelId)
    }

    fun canSendReportToConfiguredChannel(guild: Guild): Boolean {
        val channel = getReportChannel(guild) ?: return false
        return guild.selfMember.hasPermission(
            channel,
            Permission.VIEW_CHANNEL,
            Permission.MESSAGE_SEND,
            Permission.MESSAGE_EMBED_LINKS
        )
    }

    @Transactional
    fun blockUser(guildId: Long, userId: Long) {
        val settings = getSettings(guildId)
        settings.blockedUserIds.add(userId)
        reportSettingsRepository.save(settings)
    }

    @Transactional
    fun allowUser(guildId: Long, userId: Long) {
        val settings = reportSettingsRepository.findById(guildId).orElse(null) ?: return
        settings.blockedUserIds.remove(userId)
        reportSettingsRepository.save(settings)
    }

    @Transactional
    fun setUrgentRole(guildId: Long, roleId: Long) {
        val settings = getSettings(guildId)
        settings.urgentRoleId = roleId
        reportSettingsRepository.save(settings)
    }

    @Transactional
    fun setReportChannel(guildId: Long, channelId: Long) {
        val settings = getSettings(guildId)
        settings.reportChannelId = channelId
        reportSettingsRepository.save(settings)
    }

    @Transactional
    fun clearUrgentRole(guildId: Long) {
        val settings = reportSettingsRepository.findById(guildId).orElse(null) ?: return
        settings.urgentRoleId = null
        reportSettingsRepository.save(settings)
    }

    @Transactional
    fun clearReportChannel(guildId: Long) {
        val settings = reportSettingsRepository.findById(guildId).orElse(null) ?: return
        settings.reportChannelId = null
        reportSettingsRepository.save(settings)
    }

    @Transactional
    fun toggleReporting(guildId: Long): Boolean {
        val settings = getSettings(guildId)
        settings.enabled = !settings.enabled
        reportSettingsRepository.save(settings)
        return settings.enabled
    }

    companion object {
        const val EVERYONE_MENTION = "@everyone"
    }
}
