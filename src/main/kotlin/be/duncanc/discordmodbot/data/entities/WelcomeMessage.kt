package be.duncanc.discordmodbot.data.entities

import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.MessageBuilder
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.User
import org.springframework.validation.annotation.Validated
import java.awt.Color
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

    fun getWelcomeMessage(user: User): Message {
        val joinEmbed = EmbedBuilder()
            .setDescription(this.message)
            .setImage(imageUrl)
            .setColor(Color.GREEN)
            .build()
        return MessageBuilder()
            .append(user.asMention)
            .setEmbeds(joinEmbed)
            .build()
    }

    data class WelcomeMessageId(
        private val id: Long? = null,
        private val guildId: Long? = null
    ) : Serializable
}
