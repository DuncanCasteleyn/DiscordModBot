package be.duncanc.discordmodbot.moderation

import be.duncanc.discordmodbot.moderation.persistence.ReportSettings
import be.duncanc.discordmodbot.moderation.persistence.ReportSettingsRepository
import net.dv8tion.jda.api.entities.Guild
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

    fun isUserBlocked(guildId: Long, userId: Long): Boolean {
        return getSettings(guildId).blockedUserIds.contains(userId)
    }

    fun getUrgentMention(guild: Guild): String {
        val roleId = getSettings(guild.idLong).urgentRoleId ?: return EVERYONE_MENTION
        return guild.getRoleById(roleId)?.asMention ?: EVERYONE_MENTION
    }

    @Transactional
    fun blockUser(guildId: Long, userId: Long) {
        val settings = getSettings(guildId)
        settings.blockedUserIds.add(userId)
        reportSettingsRepository.save(settings)
    }

    @Transactional
    fun allowUser(guildId: Long, userId: Long) {
        val settings = getSettings(guildId)
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
    fun clearUrgentRole(guildId: Long) {
        val settings = getSettings(guildId)
        settings.urgentRoleId = null
        reportSettingsRepository.save(settings)
    }

    companion object {
        const val EVERYONE_MENTION = "@everyone"
    }
}
