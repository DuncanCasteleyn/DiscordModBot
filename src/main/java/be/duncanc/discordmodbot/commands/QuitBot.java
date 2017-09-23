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

package be.duncanc.discordmodbot.commands;

import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import org.jetbrains.annotations.NotNull;
import org.slf4j.event.Level;

import java.util.ArrayList;
import java.util.List;

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

    @Override
    public void commandExec(@NotNull MessageReceivedEvent event, @NotNull String command, String arguments) {
        if (event.getAuthor().getIdLong() == 159419654148718593L) {
            callBeforeBotQuit.forEach(beforeBotQuit -> {
                try {
                    beforeBotQuit.onAboutToQuit();
                } catch (Exception e) {
                    Companion.getLOG().log(Level.ERROR, e);
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
