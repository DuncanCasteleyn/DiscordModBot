package be.duncanc.discordmodbot.data.repositories.jpa

import be.duncanc.discordmodbot.data.entities.GuildWarnPoints
import be.duncanc.discordmodbot.data.entities.UserWarnPoints
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.test.annotation.Commit
import org.springframework.test.context.transaction.TestTransaction
import java.time.OffsetDateTime

@Disabled("Known hibernate issue with no direct fix")
@DataJpaTest
class GuildWarnPointsRepositoryTest {

    @Autowired
    private lateinit var guildWarnPointsRepository: GuildWarnPointsRepository

    @Test
    @Commit
    fun `I can save with a cascade correctly`() {
        // Given
        val data = GuildWarnPoints(0, 0)
        guildWarnPointsRepository.save(data)
        TestTransaction.end()

        val guildWarnPoints = guildWarnPointsRepository.findById(
            GuildWarnPoints.GuildWarnPointsId(
                0,
                0
            )
        ).orElseThrow()

        val userWarnPoints =
            UserWarnPoints(points = 1, creatorId = 0, reason = "test", expireDate = OffsetDateTime.now().plusDays(1))
        guildWarnPoints.points.add(userWarnPoints)

        // When
        val save = guildWarnPointsRepository.save(guildWarnPoints)
        // Then
        assert(save.points.size == 1)
        assert(save.points.first() == userWarnPoints)
    }
}
