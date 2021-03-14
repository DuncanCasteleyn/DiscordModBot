package be.duncanc.discordmodbot.data.migrators

import be.duncanc.discordmodbot.data.entities.WelcomeMessage
import be.duncanc.discordmodbot.data.repositories.jpa.GuildMemberGateRepository
import be.duncanc.discordmodbot.data.repositories.jpa.WelcomeMessageRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.*
import java.util.stream.Collectors

@Suppress("DEPRECATION")
@Component
class WelcomeMessageMigrator(
    private val welcomeMessageRepository: WelcomeMessageRepository,
    private val memberGateRepository: GuildMemberGateRepository
) : ApplicationRunner {
    companion object {
        val LOGGER: Logger = LoggerFactory.getLogger(WelcomeMessageMigrator::class.java)
    }

    @Transactional
    override fun run(args: ApplicationArguments?) {
        LOGGER.info("Performing migration of welcome messages")
        val welcomeMessages = memberGateRepository.findAll().flatMap { guildMemberGate ->
            guildMemberGate.welcomeMessages.stream().map { memberGateWelcomeMessage ->
                WelcomeMessage(
                    guildId = guildMemberGate.guildId,
                    message = memberGateWelcomeMessage.message!!,
                    imageUrl = memberGateWelcomeMessage.imageUrl!!
                )
            }.collect(Collectors.toList())
        }
        welcomeMessageRepository.saveAll(welcomeMessages)
        val memberGatesWithWelcomeMessagesRemoved = memberGateRepository.findAll().map {
            it.copy(welcomeMessages = Collections.emptySet())
        }
        memberGateRepository.saveAll(memberGatesWithWelcomeMessagesRemoved)
        LOGGER.info("Completed migration of welcome messages")
    }
}
