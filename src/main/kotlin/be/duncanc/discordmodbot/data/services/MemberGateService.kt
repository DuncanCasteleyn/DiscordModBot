/*
 * Copyright 2018 Duncan Casteleyn
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package be.duncanc.discordmodbot.data.services

import be.duncanc.discordmodbot.bot.RunBots
import be.duncanc.discordmodbot.bot.services.MemberGate
import be.duncanc.discordmodbot.data.entities.GuildMemberGate
import be.duncanc.discordmodbot.data.repositories.GuildMemberGateRepository
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.TextChannel
import org.springframework.context.annotation.Lazy
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.awt.Color
import java.time.OffsetDateTime
import java.util.*

@Service
@Transactional(readOnly = true)
class MemberGateService(
        private val guildMemberGateRepository: GuildMemberGateRepository,
        private val runBots: RunBots,
        @Lazy
        private val memberGate: MemberGate
) {
    /**
     * @return null when not configured or channel no longer exists.
     */
    fun getGateChannel(guildId: Long, jda: JDA): TextChannel? {
        val memberGate: GuildMemberGate? = guildMemberGateRepository.findById(guildId).orElse(null)
        return if (memberGate != null) {
            memberGate.gateTextChannel?.let { jda.getTextChannelById(it) }
        } else {
            null
        }
    }

    @Transactional
    fun setGateChannel(guildId: Long, gateChannel: TextChannel) {
        val memberGate: GuildMemberGate = guildMemberGateRepository.findById(guildId).orElse(GuildMemberGate(guildId))
        guildMemberGateRepository.save(memberGate.copy(gateTextChannel = gateChannel.idLong))
    }

    /**
     * @return null when not configured or channel no longer exists.
     */
    fun getWelcomeChannel(guildId: Long, jda: JDA): TextChannel? {
        val memberGate: GuildMemberGate? = guildMemberGateRepository.findById(guildId).orElse(null)
        return if (memberGate != null) {
            memberGate.welcomeTextChannel?.let { jda.getTextChannelById(it) }
        } else {
            null
        }
    }

    @Transactional
    fun setWelcomeChannel(guildId: Long, welcomeChannel: TextChannel) {
        val memberGate: GuildMemberGate = guildMemberGateRepository.findById(guildId).orElse(GuildMemberGate(guildId))
        guildMemberGateRepository.save(memberGate.copy(welcomeTextChannel = welcomeChannel.idLong))
    }

    /**
     * @return null when not configured or channel no longer exists.
     */
    fun getRulesChannel(guildId: Long, jda: JDA): TextChannel? {
        val memberGate: GuildMemberGate? = guildMemberGateRepository.findById(guildId).orElse(null)
        return if (memberGate != null) {
            memberGate.rulesTextChannel?.let { jda.getTextChannelById(it) }
        } else {
            null
        }
    }

    @Transactional
    fun setRulesChannel(guildId: Long, rulesChannel: TextChannel) {
        val memberGate = guildMemberGateRepository.findById(guildId).orElse(GuildMemberGate(guildId))
        guildMemberGateRepository.save(memberGate.copy(rulesTextChannel = rulesChannel.idLong))
    }

    /**
     * @return null when not configured or member role no longer exists.
     */
    fun getMemberRole(guildId: Long, jda: JDA): Role? {
        val memberGate: GuildMemberGate? = guildMemberGateRepository.findById(guildId).orElse(null)
        return if (memberGate != null) {
            memberGate.memberRole?.let { jda.getRoleById(it) }
        } else {
            null
        }
    }


    @Transactional
    fun setMemberRole(guildId: Long, memberRole: Role) {
        val memberGate: GuildMemberGate = guildMemberGateRepository.findById(guildId).orElse(GuildMemberGate(guildId))
        guildMemberGateRepository.save(memberGate.copy(memberRole = memberRole.idLong))
    }

    fun getQuestions(guildId: Long): Set<String> {
        val memberGate: GuildMemberGate? = guildMemberGateRepository.findById(guildId).orElse(null)
        memberGate?.questions?.size
        return if (memberGate != null) {
            Collections.unmodifiableSet(memberGate.questions)
        } else {
            Collections.emptySet()
        }
    }

    @Transactional
    fun addQuestion(guildId: Long, question: String) {
        val memberGate: GuildMemberGate = guildMemberGateRepository.findById(guildId).orElse(GuildMemberGate(guildId))
        memberGate.questions.add(question)
        guildMemberGateRepository.save(memberGate)
    }

    @Transactional
    fun removeQuestion(guildId: Long, question: String) {
        val memberGate: GuildMemberGate = guildMemberGateRepository.findById(guildId).orElse(GuildMemberGate(guildId))
        memberGate.questions.remove(question)
        guildMemberGateRepository.save(memberGate)
    }

    fun getWelcomeMessages(guildId: Long): Set<GuildMemberGate.WelcomeMessage> {
        val memberGate: GuildMemberGate? = guildMemberGateRepository.findById(guildId).orElse(null)
        memberGate?.welcomeMessages?.size
        return if (memberGate != null) {
            Collections.unmodifiableSet(memberGate.welcomeMessages)
        } else {
            Collections.emptySet()
        }
    }

    @Transactional
    fun addWelcomeMessage(guildId: Long, welcomeMessage: GuildMemberGate.WelcomeMessage) {
        val memberGate: GuildMemberGate = guildMemberGateRepository.findById(guildId).orElse(GuildMemberGate(guildId))
        memberGate.welcomeMessages.add(welcomeMessage)
        guildMemberGateRepository.save(memberGate)
    }

    @Transactional
    fun removeWelcomeMessage(guildId: Long, welcomeMessage: GuildMemberGate.WelcomeMessage) {
        val memberGate: GuildMemberGate = guildMemberGateRepository.findById(guildId).orElse(GuildMemberGate(guildId))
        memberGate.welcomeMessages.remove(welcomeMessage)
        guildMemberGateRepository.save(memberGate)
    }

    @Transactional
    fun resetGateSettings(guildId: Long) {
        val guildMemberGate: GuildMemberGate? = guildMemberGateRepository.findById(guildId).orElse(null)
        if (guildMemberGate != null) {
            guildMemberGate.questions.clear()
            guildMemberGateRepository.save(guildMemberGate.copy(gateTextChannel = null, memberRole = null, rulesTextChannel = null, removeTimeHours = null))
        }
    }

    @Transactional
    fun resetWelcomeSettings(guildId: Long) {
        val guildMemberGate: GuildMemberGate? = guildMemberGateRepository.findById(guildId).orElse(null)
        if (guildMemberGate != null) {
            guildMemberGate.welcomeMessages.clear()
            guildMemberGateRepository.save(guildMemberGate.copy(welcomeTextChannel = null))
        }
    }

    @Transactional
    fun resetAllSettings(guildId: Long) {
        guildMemberGateRepository.deleteById(guildId)
    }

    @Transactional
    fun setPurgeTime(guildId: Long, purgeTime: Long?) {
        val guildMemberGate: GuildMemberGate? = guildMemberGateRepository.findById(guildId).orElse(null)
        if (guildMemberGate != null) {
            guildMemberGateRepository.save(guildMemberGate.copy(removeTimeHours = purgeTime))
        }
    }

    @Transactional(readOnly = true)
    @Scheduled(fixedDelay = 3600000)
    fun purgeMembersWithoutRoles() {
        try {
            runBots.runningBots.forEach { jda ->
                jda.guilds.forEach { guild ->
                    val guildSettings = guildMemberGateRepository.findById(guild.idLong).orElse(null)
                    if (guildSettings?.removeTimeHours != null && guildSettings.memberRole != null) {
                        guild.members.filter {
                            val reachedTimeLimit = it.timeJoined.isBefore(OffsetDateTime.now().minusHours(guildSettings.removeTimeHours))
                            val notQueuedForApproval = !memberGate.approvalQueue.containsKey(it.user.idLong)
                            val noRoles = it.roles.size < 1
                            noRoles && reachedTimeLimit && notQueuedForApproval
                        }.forEach { member ->
                            val userKickNotification = EmbedBuilder()
                                    .setColor(Color.RED)
                                    .setTitle("${guild.name}: You have been kicked", null)
                                    .setDescription("Reason: You did not complete the server entry process within ${guildSettings.removeTimeHours} hour(s)")
                                    .build()
                            member.user.openPrivateChannel().queue(
                                    {
                                        it.sendMessage(userKickNotification).queue({ guild.kick(member).queue() }, { guild.kick(member).queue() })
                                    },
                                    {
                                        guild.kick(member).queue()
                                    })
                        }
                    }
                }
            }
        } catch (ignored: UninitializedPropertyAccessException) {
            // Ignored
        }
    }
}
