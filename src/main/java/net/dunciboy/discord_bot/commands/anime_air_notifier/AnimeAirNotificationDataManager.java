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

import java.time.DayOfWeek;
import java.time.OffsetTime;
import java.util.concurrent.TimeUnit;

/**
 * Created by Duncan on 10/03/2017.
 */
class AnimeAirNotificationDataManager extends CommandModule {
    private static final String[] ALIASES = new String[]{"AnimeAirNotificationDataManager", "AAndM"};
    private static final String ARGUMENTATION_SYNTAX = "Unknown for now";
    private static final String DESCRIPTION = "This command is used to control the data that is stored in the data system for airing anime.";

    /**
     * Constructor for class
     */
    AnimeAirNotificationDataManager() {
        super(ALIASES, ARGUMENTATION_SYNTAX, DESCRIPTION);
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
        String[] args = arguments.toLowerCase().split(" ");
        switch (args[0]) {
            case "add":
                AnimeAirNotifier.addAnime(new AnimeAirNotifier.AnimeAirInfo(DayOfWeek.of(Integer.parseInt(args[1])), args[2], OffsetTime.parse(args[3]), event.getAuthor().getId(), args[4]));
                break;
            case "remove":
                if (AnimeAirNotifier.removeAnime(Integer.parseInt(args[1]))) {
                    event.getAuthor().openPrivateChannel().queue(privateChannel -> privateChannel.sendMessage("The anime was successfully removed.").queue());
                } else {
                    event.getAuthor().openPrivateChannel().queue(privateChannel -> privateChannel.sendMessage("The anime could not be removed because it did not exist.").queue());
                }
                break;
            case "edit":
                AnimeAirNotifier.AnimeAirInfo animeAirInfo = AnimeAirNotifier.getAnime().get(Integer.parseInt(args[1]));
                switch (args[2]) {
                    case "name":
                        animeAirInfo.setAnimeName(arguments.substring(args[0].length() + args[1].length() + args[2].length() + 2));
                        break;
                    case "airedepisodes":
                        animeAirInfo.setAiredEpisodes(Integer.parseInt(args[3]));
                        break;
                    case "maxepisodes":
                        animeAirInfo.setMaxEpisodes(Integer.parseInt(args[3]));
                        break;
                    case "malurl":
                        animeAirInfo.setMalUrl(args[3]);
                        break;
                    case "airday":
                        animeAirInfo.setAirDay(DayOfWeek.of(Integer.parseInt(args[3])));
                        break;
                    case "airtime":
                        animeAirInfo.setAirTime(OffsetTime.parse(arguments.substring(args[0].length() + args[1].length() + args[2].length() + 2)));
                    case "isnotairing":
                        animeAirInfo.setNotAiring(Boolean.parseBoolean(args[3]));
                        break;
                    case "aired":
                        animeAirInfo.setAired(Boolean.parseBoolean(args[3]));
                        break;
                    default:
                        event.getAuthor().openPrivateChannel().queue(privateChannel -> privateChannel.sendMessage("unknown information field for anime info").queue());
                        break;
                }
                break;
            case "suggestedit":
                event.getAuthor().openPrivateChannel().queue(privateChannel -> privateChannel.sendMessage("Not yet implemented.").queue());
                break;
            case "approve":
                AnimeAirNotifier.getAnime().get(Integer.parseInt(args[1])).accept();
                break;
            case "help:":
                //todo create help for this module
                break;
            default:
                event.getAuthor().openPrivateChannel().queue(privateChannel -> privateChannel.sendMessage("Unknown sub command.").queue());
        }
    }
}
