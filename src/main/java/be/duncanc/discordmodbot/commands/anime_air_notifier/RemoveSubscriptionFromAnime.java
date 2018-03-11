/*
 * MIT License
 *
 * Copyright (c) 2017 Duncan Casteleyn
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */

package be.duncanc.discordmodbot.commands.anime_air_notifier;

import be.duncanc.discordmodbot.commands.CommandModule;
import be.duncanc.discordmodbot.services.AnimeAirNotifier;
import net.dv8tion.jda.core.entities.ChannelType;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;

/**
 * Created by Duncan on 10/03/2017.
 */
class RemoveSubscriptionFromAnime extends CommandModule {
    private static final String[] ALIASES = new String[]{"RemoveSubscriptionFromAnime", "RSfa", "UnSubscribe"};
    private static final String ARGUMENTATION_SYNTAX = "[Anime id]";
    private static final String DESCRIPTION = "This command removes the anime id from your subscription list.";

    RemoveSubscriptionFromAnime() {
        super(ALIASES, ARGUMENTATION_SYNTAX, DESCRIPTION);
    }

    @Override
    public void commandExec(@NotNull MessageReceivedEvent event, @NotNull String command, String arguments) {
        if (!event.isFromType(ChannelType.PRIVATE)) {
            event.getChannel().sendMessage("This command only works in DM.").queue(message -> message.delete().completeAfter(1, TimeUnit.MINUTES));
            return;
        }
        if (arguments != null) {
            if (AnimeAirNotifier.removeSubscription(event.getAuthor().getIdLong(), Integer.parseInt(arguments))) {
                event.getAuthor().openPrivateChannel().queue(privateChannel -> privateChannel.sendMessage("The anime was removed from your subscription list.").queue());
            } else {
                event.getAuthor().openPrivateChannel().queue(privateChannel -> privateChannel.sendMessage("The anime could not be found in your subscription list.").queue());
            }
        } else {
            event.getAuthor().openPrivateChannel().queue(privateChannel -> privateChannel.sendMessage("You are required to enter an anime id.").queue());
        }
    }
}
