package be.duncanc.discordmodbot.voting.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface VotingEmotesRepository : JpaRepository<VoteEmotes, Long>
