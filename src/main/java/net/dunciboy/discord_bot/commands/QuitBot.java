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

package net.dunciboy.discord_bot.commands;

import net.dv8tion.jda.core.events.message.MessageReceivedEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Created by Duncan on 3/03/2017.
 * <p>
 * Quit command for the bot
 */
public class QuitBot extends CommandModule {
    private static final String[] ALIASES = new String[]{"Quit", "Shutdown", "Disconnect"};
    private static final String DESCRIPTION = "Shuts down the bot.";

    private List<BeforeBotQuit> callBeforeBotQuit;

    public QuitBot() {
        super(ALIASES, null, DESCRIPTION);
        callBeforeBotQuit = new ArrayList<>();
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
        if (event.getAuthor().getIdLong() == 159419654148718593L) {
            callBeforeBotQuit.forEach(beforeBotQuit -> {
                try {
                    beforeBotQuit.onAboutToQuit();
                } catch (Exception e) {
                    Companion.getLOG().log(e);
                }
            });
            event.getChannel().sendMessage(event.getAuthor().getAsMention() + " **Shutting down... :wave: **").queue();
            /*try {
                TimeUnit.SECONDS.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }*/
            event.getJDA().shutdown();
        } else {
            event.getChannel().sendMessage(event.getAuthor().getAsMention() + " you don't have permission to shutdown the bot!").queue();
        }

    }

    public void addCallBeforeQuit(BeforeBotQuit beforeBotQuit) {
        callBeforeBotQuit.add(beforeBotQuit);
    }

    /**
     * This interface can be used to perform actions before quiting
     */
    public interface BeforeBotQuit {
        /**
         * Actions to perform before the quit command is finished with executing.
         */
        void onAboutToQuit();
    }
}
