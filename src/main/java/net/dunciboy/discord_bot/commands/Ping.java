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
        event.getChannel().sendMessage("pong!\nIt took discord " + event.getJDA().getPing() + " milliseconds to respond to our last heartbeat.").queue(message -> message.editMessage(message.getRawContent() + "\nIt took Discord " + (System.currentTimeMillis() - millisBeforeRequest) + "milliseconds to process this message.").queue());
    }
}
