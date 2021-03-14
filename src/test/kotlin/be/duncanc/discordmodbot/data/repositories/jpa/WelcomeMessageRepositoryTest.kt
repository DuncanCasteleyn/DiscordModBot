package be.duncanc.discordmodbot.data.repositories.jpa

import be.duncanc.discordmodbot.data.entities.WelcomeMessage
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.dao.NonTransientDataAccessException
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.transaction.TestTransaction
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@DataJpaTest
class WelcomeMessageRepositoryTest {
    companion object {
        private const val MAX_SIZE_MESSAGE =
            "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"
    }

    @Autowired
    private lateinit var welcomeMessageRepository: WelcomeMessageRepository

    @Test
    @DirtiesContext
    fun `Welcomes messages can be 2048 characters long`() {
        // Given
        val welcomeMessage = WelcomeMessage(guildId = 0, imageUrl = "test", message = MAX_SIZE_MESSAGE)
        val save = welcomeMessageRepository.save(welcomeMessage)
        TestTransaction.flagForCommit()
        TestTransaction.end()
        TestTransaction.start()
        // When
        val dbWelcomeMessage =
            welcomeMessageRepository.findById(WelcomeMessage.WelcomeMessageId(save.id, save.guildId)).orElseThrow()
        // Then
        Assertions.assertEquals(welcomeMessage.imageUrl, dbWelcomeMessage.imageUrl)
        Assertions.assertEquals(welcomeMessage.message, dbWelcomeMessage.message)
    }

    @Test
    @Transactional(propagation = Propagation.NEVER)
    fun `Welcomes messages can not be 2049 characters long`() {
        // Given
        val welcomeMessage = WelcomeMessage(guildId = 0, imageUrl = "test", message = MAX_SIZE_MESSAGE + "h")
        // When
        val assertThrows =
            assertThrows<NonTransientDataAccessException> { welcomeMessageRepository.save(welcomeMessage) }
        // Then
        assert(assertThrows.message!!.startsWith("could not execute statement;"))
    }
}
