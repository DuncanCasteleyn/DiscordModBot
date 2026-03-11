package be.duncanc.discordmodbot.moderation.persistence



import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.test.annotation.Commit
import org.springframework.test.context.TestConstructor
import org.springframework.test.context.transaction.TestTransaction
import java.time.OffsetDateTime

@Disabled("Known hibernate issue with no direct fix")
@DataJpaTest
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class GuildWarnPointRepositoryTest(
    private val guildWarnPointsRepository: GuildWarnPointsRepository
) {

    @Test
    @Commit
    fun `I can save correctly`() {
        // Given
        val data = GuildWarnPoint(
            0,
            0,
            points = 1,
            creatorId = 0,
            reason = "test",
            expireDate = OffsetDateTime.now().plusDays(1)
        )
        guildWarnPointsRepository.save(data)

        // When
        guildWarnPointsRepository.save(data)
        TestTransaction.end()
        // Then
        val guildWarnPoints = guildWarnPointsRepository.findAllByGuildIdAndUserIdAndExpireDateAfter(
            0,
            0,
            OffsetDateTime.now()
        )

        assert(guildWarnPoints.size == 1)
        assert(guildWarnPoints.first().reason == "test")
    }
}
