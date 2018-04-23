package be.duncanc.discordmodbot.bot.commands

import be.duncanc.discordmodbot.data.entities.ReportChannel
import be.duncanc.discordmodbot.data.repositories.ReportChannelRepository
import net.dv8tion.jda.core.Permission
import net.dv8tion.jda.core.events.ReadyEvent
import net.dv8tion.jda.core.events.message.MessageReceivedEvent
import org.apache.naming.factory.BeanFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.BeanFactoryAnnotationUtils
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

@Component
class Feedback private constructor() : CommandModule(arrayOf("Feedback", "Report"), null, "This command allows users to give feedback to the server staff by posting it in a channel that is configured") {

    @Autowired
    private lateinit var reportChannelRepository: ReportChannelRepository
    private val setFeedbackChannel = SetFeedbackChannel()
    private val disableFeedback = DisableFeedback()

    override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
        TODO("not implemented")
    }

    override fun onReady(event: ReadyEvent) {
        event.jda.addEventListener(setFeedbackChannel, disableFeedback)
    }

    inner class SetFeedbackChannel : CommandModule(arrayOf("SetFeedbackChannel"), null, "This command sets the feedback channel enabling this module for the server.", ignoreWhiteList = true, requiredPermissions = *arrayOf(Permission.MANAGE_CHANNEL)) {
        override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
            reportChannelRepository.save(ReportChannel(event.guild.idLong, event.textChannel.idLong))
        }
    }

    inner class DisableFeedback : CommandModule(arrayOf("DisableFeedback"), null, "This command disables the feedback system for the server.", ignoreWhiteList = true, requiredPermissions = *arrayOf(Permission.MANAGE_CHANNEL)) {
        override fun commandExec(event: MessageReceivedEvent, command: String, arguments: String?) {
            reportChannelRepository.deleteById(event.guild.idLong)
        }
    }
}