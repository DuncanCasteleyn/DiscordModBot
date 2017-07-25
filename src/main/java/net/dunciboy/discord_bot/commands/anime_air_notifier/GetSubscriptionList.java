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
import net.dv8tion.jda.core.entities.ChannelType;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;

import java.util.concurrent.TimeUnit;

/**
 * Get the anime a user is subscribed to.
 * <p>
 * Created by Duncan on 5/04/2017.
 */
class GetSubscriptionList extends CommandModule {
    private static final String[] ALIASES = new String[]{"GetSubscriptionList", "GetSubscriptions", "GSL"};
    private static final String DESCRIPTION = "Get the list of your current subscribed anime.";

    /**
     * Constructor for class
     */
    GetSubscriptionList() {
        super(ALIASES, null, DESCRIPTION);
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
        AnimeAirNotifier.getSubscriptionList(event.getAuthor().getIdLong()).forEach((integer, animeAirInfo) -> stringBuilder.append(integer).append('\t').append(animeAirInfo.toString()).append('\n'));
        //todo send message with list
    }
}
