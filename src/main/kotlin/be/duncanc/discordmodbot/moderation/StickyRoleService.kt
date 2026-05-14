package be.duncanc.discordmodbot.moderation

import be.duncanc.discordmodbot.discord.nicknameAndUsername
import be.duncanc.discordmodbot.logging.GuildLogger
import be.duncanc.discordmodbot.moderation.persistence.StickyRoleConfig
import be.duncanc.discordmodbot.moderation.persistence.StickyRoleConfigRepository
import be.duncanc.discordmodbot.moderation.persistence.StickyRoleSnapshot
import be.duncanc.discordmodbot.moderation.persistence.StickyRoleSnapshotRepository
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Role
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.awt.Color

@Service
@Transactional(readOnly = true)
class StickyRoleService(
    private val stickyRoleConfigRepository: StickyRoleConfigRepository,
    private val stickyRoleSnapshotRepository: StickyRoleSnapshotRepository,
    private val guildLogger: GuildLogger
) {
    companion object {
        private val LOG = LoggerFactory.getLogger(StickyRoleService::class.java)
        private const val RESTORE_REASON = "Restoring previously saved sticky roles on rejoin"
    }

    fun getConfiguredRoleIds(guildId: Long): Set<Long> {
        return stickyRoleConfigRepository.findById(guildId).orElse(null)?.roleIds ?: emptySet()
    }

    @Transactional
    fun addConfiguredRole(guildId: Long, roleId: Long) {
        val config = stickyRoleConfigRepository.findById(guildId).orElse(StickyRoleConfig(guildId))
        config.roleIds.add(roleId)
        stickyRoleConfigRepository.save(config)
    }

    @Transactional
    fun removeConfiguredRole(guildId: Long, roleId: Long) {
        stickyRoleConfigRepository.findById(guildId).ifPresent { config ->
            config.roleIds.remove(roleId)
            if (config.roleIds.isEmpty()) {
                stickyRoleConfigRepository.delete(config)
            } else {
                stickyRoleConfigRepository.save(config)
            }
        }

        removeRoleFromSnapshots(guildId, roleId)
    }

    @Transactional
    fun clearConfiguredRoles(guildId: Long) {
        stickyRoleConfigRepository.deleteById(guildId)
        stickyRoleSnapshotRepository.deleteAllByGuildId(guildId)
    }

    @Transactional
    fun captureRolesOnLeave(guildId: Long, userId: Long, memberRoleIds: Collection<Long>) {
        val configuredRoleIds = getConfiguredRoleIds(guildId)
        if (configuredRoleIds.isEmpty()) {
            deleteSnapshot(guildId, userId)
            return
        }

        val savedRoleIds = memberRoleIds.filterTo(linkedSetOf()) { it in configuredRoleIds }
        if (savedRoleIds.isEmpty()) {
            deleteSnapshot(guildId, userId)
            return
        }

        stickyRoleSnapshotRepository.save(StickyRoleSnapshot(guildId, userId, savedRoleIds))
    }

    @Transactional
    fun restoreRolesOnJoin(guild: Guild, member: Member) {
        val snapshotId = StickyRoleSnapshot.StickyRoleSnapshotId(guild.idLong, member.idLong)
        val snapshot = stickyRoleSnapshotRepository.findById(snapshotId).orElse(null) ?: return
        val configuredRoleIds = getConfiguredRoleIds(guild.idLong)
        val rolesToRestore = snapshot.roleIds
            .asSequence()
            .filter { it in configuredRoleIds }
            .mapNotNull(guild::getRoleById)
            .filter(::isRestorableRole)
            .filter { role -> member.roles.none { it.idLong == role.idLong } }
            .toList()

        if (rolesToRestore.isEmpty()) {
            return
        }

        if (!guild.selfMember.hasPermission(Permission.MANAGE_ROLES)) {
            LOG.warn("Unable to restore sticky roles for {} in guild {} due to missing manage roles permission", member.id, guild.id)
            return
        }

        guild.modifyMemberRoles(member, rolesToRestore, null)
            .reason(RESTORE_REASON)
            .queue(
                {
                    deleteSnapshot(guild.idLong, member.idLong)
                    logStickyRoleRestore(guild, member, rolesToRestore)
                },
                { throwable ->
                    LOG.warn("Failed to restore sticky roles for {} in guild {}", member.id, guild.id, throwable)
                }
            )
    }

    @Transactional
    fun removeDeletedRole(guildId: Long, roleId: Long) {
        removeConfiguredRole(guildId, roleId)
    }

    @Transactional
    fun clearGuildState(guildId: Long) {
        stickyRoleConfigRepository.deleteById(guildId)
        stickyRoleSnapshotRepository.deleteAllByGuildId(guildId)
    }

    private fun isRestorableRole(role: Role): Boolean {
        return !role.isPublicRole && !role.isManaged && role.guild.selfMember.canInteract(role)
    }

    @Transactional
    fun deleteSnapshot(guildId: Long, userId: Long) {
        stickyRoleSnapshotRepository.deleteById(StickyRoleSnapshot.StickyRoleSnapshotId(guildId, userId))
    }

    private fun removeRoleFromSnapshots(guildId: Long, roleId: Long) {
        stickyRoleSnapshotRepository.findAllByGuildId(guildId).forEach { snapshot ->
            if (!snapshot.roleIds.remove(roleId)) {
                return@forEach
            }

            if (snapshot.roleIds.isEmpty()) {
                stickyRoleSnapshotRepository.delete(snapshot)
            } else {
                stickyRoleSnapshotRepository.save(snapshot)
            }
        }
    }

    private fun logStickyRoleRestore(guild: Guild, member: Member, restoredRoles: List<Role>) {
        val logEmbed = EmbedBuilder()
            .setColor(Color.YELLOW)
            .setTitle("Sticky roles restored")
            .addField("User", member.nicknameAndUsername, true)
            .addField("Roles", restoredRoles.joinToString("\n") { it.asMention }, false)
            .addField("Reason", "Previously saved sticky roles were restored on rejoin", false)

        guildLogger.log(
            guild = guild,
            associatedUser = member.user,
            logEmbed = logEmbed,
            actionType = GuildLogger.LogTypeAction.MODERATOR
        )
    }
}
