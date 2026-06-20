package be.duncanc.discordmodbot.logging

import org.springframework.security.crypto.encrypt.Encryptors
import org.springframework.stereotype.Component

@Component
class MessageContentEncryptor(
    messageEncryptionProperties: MessageEncryptionProperties,
) {
    private val textEncryptor = Encryptors.delux(
        messageEncryptionProperties.password,
        messageEncryptionProperties.salt,
    )

    fun encrypt(content: String): String = textEncryptor.encrypt(content)

    fun decrypt(content: String): String = textEncryptor.decrypt(content)
}
