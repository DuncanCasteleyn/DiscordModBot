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

package net.dunciboy.discord_bot.commands;

import net.dv8tion.jda.core.events.message.MessageReceivedEvent;

/**
 * Created by Duncan on 11/02/2017.
 * <p>
 * Interface to force implementation for commands
 */
public class Ping extends CommandModule {
    private static final String[] ALIASES = new String[]{"Ping"};
    private static final String DESCRIPTION = "responds with \"pong!\".";

    public Ping() {
        super(ALIASES, null, DESCRIPTION);
    }

    /**
     * Do something with the event, command and arguments.
     *
     * @param event     A MessageReceivedEvent that came with the command
     * @param command   Not used here
     * @param arguments Not used here
     */
    @Override
    public void commandExec(MessageReceivedEvent event, String command, String arguments) {
        long millisBeforeRequest = System.currentTimeMillis();
        event.getChannel().sendMessage("pong!\nIt took Discord " + event.getJDA().getPing() + " milliseconds to respond to our last heartbeat.").queue(message -> message.editMessage(message.getRawContent() + "\nIt took Discord " + (System.currentTimeMillis() - millisBeforeRequest) + " milliseconds to process this message.").queue());
    }
}
