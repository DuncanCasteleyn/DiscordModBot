package be.duncanc.discordmodbot.data.entities

import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder
import net.dv8tion.jda.api.utils.messages.MessageCreateData
import org.hibernate.Hibernate
import org.springframework.validation.annotation.Validated
import java.awt.Color
import java.io.Serializable
import java.util.*

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

    fun getWelcomeMessage(user: User): MessageCreateData {
        val joinEmbed = EmbedBuilder()
            .setDescription(this.message)
            .setImage(imageUrl)
            .setColor(Color.GREEN)
            .build()
        return MessageCreateBuilder()
            .addContent(user.asMention)
            .setEmbeds(joinEmbed)
            .build()
    }

    data class WelcomeMessageId(
        private val id: Long? = null,
        private val guildId: Long? = null
    ) : Serializable

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) return false
        other as WelcomeMessage

        return id == other.id
                && guildId == other.guildId
    }

    override fun hashCode(): Int = Objects.hash(id, guildId)

    @Override
    override fun toString(): String {
        return this::class.simpleName + "(id = $id , guildId = $guildId )"
    }
}
