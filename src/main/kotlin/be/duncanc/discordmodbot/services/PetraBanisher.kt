package be.duncanc.discordmodbot.services

import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.core.events.message.guild.react.GuildMessageReactionAddEvent
import net.dv8tion.jda.core.hooks.ListenerAdapter
import java.util.concurrent.TimeUnit

object PetraBanisher : ListenerAdapter() {
    override fun onGuildMessageReceived(event: GuildMessageReceivedEvent) {
        if(event.channel.idLong == 429723980497158154L || event.author == event.jda.selfUser) {
            return
        }

        val text = event.message.contentStripped
        if(text.contains("Petra ", ignoreCase = true) || text.contains(" Petra", ignoreCase = true) || text.equals("Petra", true)) {
            banishUser(event,  "offensive language.")

        } else if(text.contains("QR", ignoreCase = true)) {
            banishUser(event, "https://i.imgur.com/Tjwibu0.png.")

        } else if(text.contains("lol ") || text.contains(" lol") || text.equals("lol", true) || text.contains("lmao ") || text.contains(" lmao") || text.equals("lmao", true)) {
            banishUser(event, "expressing emotion.")
        }
    }

    private fun banishUser(event: GuildMessageReceivedEvent, reason: String) {
        event.guild.controller.addSingleRoleToMember(event.member, event.guild.getRoleById(429770550668427284L)).reason("Spoke of a forbidden word.").queue()
        event.channel.sendMessage(event.member.asMention + " was banished for " + reason).queue { it.delete().queueAfter(5, TimeUnit.MINUTES) }
    }

    override fun onGuildMessageReactionAdd(event: GuildMessageReactionAddEvent) {
        if(event.messageIdLong == 429956741422383104L && event.reactionEmote.name == "\uD83D\uDE4F\uD83C\uDFFC") {
            event.guild.controller.removeSingleRoleFromMember(event.member, event.guild.getRoleById(429770550668427284L)).reason("Prayed.").queueAfter(1, TimeUnit.MINUTES)
            event.reaction.removeReaction(event.user).queue()
        }
    }


}