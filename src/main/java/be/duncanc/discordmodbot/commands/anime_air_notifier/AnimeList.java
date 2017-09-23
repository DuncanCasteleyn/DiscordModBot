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

import be.duncanc.discordmodbot.AnimeAirNotifier;
import be.duncanc.discordmodbot.commands.CommandModule;
import net.dv8tion.jda.core.MessageBuilder;
import net.dv8tion.jda.core.entities.ChannelType;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;

import java.util.Queue;
import java.util.concurrent.TimeUnit;

/**
 * Created by Duncan on 10/03/2017.
 */
class AnimeList extends CommandModule {
    AnimeList() {
        super(new String[]{"AnimeList", "AL"}, "(optional) [Search term]", "This command cane be used to get a list of anime names and the ids linked towards them.");
    }

    @Override
    public void commandExec(MessageReceivedEvent event, String command, String arguments) {
        if (!event.isFromType(ChannelType.PRIVATE)) {
            event.getChannel().sendMessage("This command only works in DM.").queue(message -> message.delete().completeAfter(1, TimeUnit.MINUTES));
            return;
        }
        StringBuilder stringBuilder = new StringBuilder();
        AnimeAirNotifier.getAnime().forEach((integer, animeAirInfo) -> {
            if (animeAirInfo.isAccepted()) {
                stringBuilder.append(integer).append('\t').append(animeAirInfo.toString()).append('\n');
            }
        });
        Queue<Message> messages = new MessageBuilder().appendCodeBlock(stringBuilder.toString(), "text").buildAll(MessageBuilder.SplitPolicy.SPACE);
        event.getAuthor().openPrivateChannel().queue(privateChannel -> {
            Message message = messages.poll();
            do {
                privateChannel.sendMessage(message).queue();
                message = messages.poll();
            } while (message != null);
        });
    }
}
