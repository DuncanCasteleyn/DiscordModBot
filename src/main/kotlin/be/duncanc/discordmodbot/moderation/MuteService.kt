package be.duncanc.discordmodbot.moderation

import be.duncanc.discordmodbot.moderation.persistence.MuteRole
import be.duncanc.discordmodbot.moderation.persistence.MuteRolesRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class MuteService(
    private val muteRolesRepository: MuteRolesRepository
) {
    @Transactional
    fun muteUserById(guildId: Long, userId: Long) {
        val muteRole = muteRolesRepository.findById(guildId)
            .orElseThrow { IllegalStateException("This guild does not have a mute role set up.") }
        muteRole.mutedUsers.add(userId)
        muteRolesRepository.save(muteRole)
    }

    @Transactional
    fun unmuteUserById(guildId: Long, userId: Long) {
        muteRolesRepository.findById(guildId).ifPresent { muteRole ->
            muteRole.mutedUsers.remove(userId)
            muteRolesRepository.save(muteRole)
        }
    }

    @Transactional
    fun setMuteRole(guildId: Long, roleId: Long) {
        muteRolesRepository.save(MuteRole(guildId, roleId))
    }

    @Transactional
    fun removeMuteRole(guildId: Long) {
        muteRolesRepository.deleteById(guildId)
    }

    @Transactional
    fun deleteMuteRoleByRoleId(guildId: Long, roleId: Long) {
        muteRolesRepository.deleteByRoleIdAndGuildId(roleId, guildId)
    }

    @Transactional
    fun addMutedUserIfHasRole(guildId: Long, roleId: Long, userId: Long): Boolean {
        val muteRole = muteRolesRepository.findById(guildId).orElse(null) ?: return false
        if (muteRole.roleId == roleId) {
            muteRole.mutedUsers.add(userId)
            muteRolesRepository.save(muteRole)
            return true
        }
        return false
    }

    @Transactional
    fun removeMutedUserIfHasRole(guildId: Long, roleId: Long, userId: Long): Boolean {
        val muteRole = muteRolesRepository.findById(guildId).orElse(null) ?: return false
        if (muteRole.roleId == roleId) {
            muteRole.mutedUsers.remove(userId)
            muteRolesRepository.save(muteRole)
            return true
        }
        return false
    }

    @Transactional
    fun muteOrUnmuteUser(guildId: Long, roleId: Long, userId: Long, hasRole: Boolean) {
        val muteRole = muteRolesRepository.findById(guildId).orElse(null) ?: return
        if (muteRole.roleId == roleId) {
            if (hasRole) {
                muteRole.mutedUsers.add(userId)
            } else {
                muteRole.mutedUsers.remove(userId)
            }
            muteRolesRepository.save(muteRole)
        }
    }

    @Transactional(readOnly = true)
    fun isUserMuted(guildId: Long, userId: Long): Boolean {
        val muteRole = muteRolesRepository.findById(guildId).orElse(null) ?: return false
        return muteRole.mutedUsers.contains(userId)
    }

    @Transactional(readOnly = true)
    fun getMuteRoleId(guildId: Long): Long? {
        return muteRolesRepository.findById(guildId).orElse(null)?.roleId
    }

    @Transactional
    fun addMutedUser(guildId: Long, userId: Long) {
        muteRolesRepository.findById(guildId).ifPresent { muteRole ->
            muteRole.mutedUsers.add(userId)
            muteRolesRepository.save(muteRole)
        }
    }

    @Transactional
    fun removeMutedUser(guildId: Long, userId: Long) {
        muteRolesRepository.findById(guildId).ifPresent { muteRole ->
            muteRole.mutedUsers.remove(userId)
            muteRolesRepository.save(muteRole)
        }
    }
}
