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
