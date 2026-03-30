package be.duncanc.discordmodbot.reporting

import be.duncanc.discordmodbot.reporting.persistence.ActivityReportSettingsRepository
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.events.session.ReadyEvent
import net.dv8tion.jda.api.events.session.ShutdownEvent
import net.dv8tion.jda.api.exceptions.PermissionException
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.utils.SplitUtil
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.OffsetDateTime

@Component
class WeeklyActivityReport(
    private val activityReportSettingsRepository: ActivityReportSettingsRepository
) : ListenerAdapter() {
    companion object {
        val LOG: Logger = LoggerFactory.getLogger(WeeklyActivityReport::class.java)
    }

    val instances = HashSet<JDA>()

    override fun onReady(event: ReadyEvent) {
        instances.add(event.jda)
    }

    override fun onShutdown(event: ShutdownEvent) {
        instances.remove(event.jda)
    }

    @Deprecated("Broken needs to be fixed")
    fun sendReports() {
        val statsCollectionStartTime = OffsetDateTime.now()
        activityReportSettingsRepository.findAll().forEach { (idOfGuild, reportChannel, trackedRoleOrMember) ->
            val guild = idOfGuild.let { guildId ->
                instances.stream().filter { jda ->
                    jda.getGuildById(guildId) != null
                }.findFirst().orElse(null)?.getGuildById(guildId)
            }
            if (guild != null) {
                val textChannel = reportChannel?.let { guild.getTextChannelById(it) }
                if (textChannel != null) {
                    val trackedMembers = HashSet<Member>()
                    trackedRoleOrMember.forEach {
                        val role = guild.getRoleById(it)
                        if (role != null) {
                            val members = guild.getMembersWithRoles(role)
                            trackedMembers.addAll(members)
                        } else {
                            val member = guild.getMemberById(it)!!
                            trackedMembers.add(member)
                        }
                    }
                    if (trackedMembers.isEmpty()) {
                        return@forEach
                    }
                    val stats = HashMap<TextChannel, HashMap<Member, Long>>()
                    guild.textChannels.forEach {
                        val channelStats = HashMap<Member, Long>()
                        trackedMembers.forEach { trackedMember -> channelStats[trackedMember] = 0L }
                        try {
                            for (message in it.iterableHistory) {
                                if (trackedMembers.contains(message.member)) {
                                    channelStats[message.member!!] = (channelStats[message.member!!] ?: 0L) + 1L
                                }
                                if (Duration.between(message.timeCreated, statsCollectionStartTime).toDays() >= 7) {
                                    break
                                }
                            }
                            stats[it] = channelStats
                        } catch (e: PermissionException) {
                            LOG.warn("Insufficient permissions to retrieve history from $it")
                        }
                    }
                    val message = StringBuilder()
                    message.append("**Message statistics of the past 7 days**\n")
                    stats.forEach { (channel, channelStats) ->
                        message.append("\n***${channel.asMention}***\n\n")
                        channelStats.forEach { (member, count) ->
                            message.append("${member.user.name}: $count\n")
                        }
                    }

                    SplitUtil.split(message.toString(), Message.MAX_CONTENT_LENGTH, SplitUtil.Strategy.NEWLINE)
                        .forEach {
                            textChannel.sendMessage(it).queue()
                        }
                } else {
                    LOG.warn("The text channel with id $reportChannel was not found on the server/guild. Configure another channel.")
                }
            } else {
                LOG.warn("The guild with id $idOfGuild was not found, maybe the bot was removed or maybe you shut down the bot responsible for this server.")
            }
        }
    }
}
