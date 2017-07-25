/*
 * Copyright 2017 Duncan C.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.dunciboy.discord_bot.commands.anime_air_notifier;

import net.dunciboy.discord_bot.AnimeAirNotifier;
import net.dunciboy.discord_bot.commands.CommandModule;
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
    /**
     * Constructor for class
     */
    AnimeList() {
        super(new String[]{"AnimeList", "AL"}, "(optional) [Search term]", "This command cane be used to get a list of anime names and the ids linked towards them.");
    }

    /**
     * Do something with the event, command and arguments.
     *
     * @param event     A MessageReceivedEvent that came with the command
     * @param command   The command alias that was used to trigger this commandExec
     * @param arguments The arguments that where entered after the command alias
     */
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
