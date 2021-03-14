package be.duncanc.discordmodbot.data.entities

import org.springframework.validation.annotation.Validated
import java.io.Serializable
import javax.persistence.*
import javax.validation.constraints.NotBlank

@IdClass(WelcomeMessage.WelcomeMessageId::class)
@Validated
@Entity
@Table(name = "welcome_messages")
data class WelcomeMessage(
    @Id
    @GeneratedValue(generator = "welcome_message_id_seq")
    @SequenceGenerator(name = "welcome_message_id_seq", sequenceName = "welcome_message_seq", allocationSize = 1)
    @Column(insertable = false, updatable = false)
    val id: Long = 0L,
    @Id
    val guildId: Long,
    @Column(nullable = false)
    @field:NotBlank
    val imageUrl: String = "",
    @Column(nullable = false, length = 2048)
    @field:NotBlank
    val message: String = ""
) {
    data class WelcomeMessageId(
        private val id: Long? = null,
        private val guildId: Long? = null
    ) : Serializable
}
