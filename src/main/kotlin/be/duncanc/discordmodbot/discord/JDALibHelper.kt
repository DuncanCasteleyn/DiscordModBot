package be.duncanc.discordmodbot.discord

import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel

/**
 * Retrieves a string that contains both the nickname and username of a member.
 */
val Member.nicknameAndUsername: String
    get() = if (this.nickname != null) {
        "${this.nickname}(${this.user.name})"
    } else {
        this.user.name
    }

/**
 * Deletes multiple messages at once; unlike the default method, this one will split the messages in stacks of 100 messages each automatically.
 *
 * @param messagesIds Messages to delete.
 */
fun TextChannel.limitLessBulkDeleteByIds(messagesIds: Collection<Long>) {
    val messagesIdStrings = messagesIds.map { it.toULong().toString() }.toMutableList()
    if (messagesIdStrings.size in 2..100) {
        this.deleteMessagesByIds(messagesIdStrings).queue()
    } else if (messagesIdStrings.size < 2) {
        for (message in messagesIdStrings) {
            this.deleteMessageById(message).queue()
        }
    } else {
        var messagesStack = ArrayList<String>()
        while (messagesIdStrings.isNotEmpty()) {
            messagesStack.add(messagesIdStrings.removeAt(0))
            if (messagesStack.size == 100) {
                this.deleteMessagesByIds(messagesStack).queue()
                messagesStack = ArrayList()
            }
        }
        if (messagesStack.size >= 2) {
            this.deleteMessagesByIds(messagesStack).queue()
        } else {
            for (message in messagesStack) {
                this.deleteMessageById(message).queue()
            }
        }
    }
}
