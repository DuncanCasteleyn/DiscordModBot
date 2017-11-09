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
 */

package be.duncanc.discordmodbot;

import be.duncanc.discordmodbot.commands.CommandModule;
import be.duncanc.discordmodbot.sequence.Sequence;
import be.duncanc.discordmodbot.utils.jsontojavaobject.JSONKey;
import be.duncanc.discordmodbot.utils.jsontojavaobject.JSONToJavaObject;
import net.dv8tion.jda.core.entities.MessageChannel;
import net.dv8tion.jda.core.entities.User;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class IAmRoles extends CommandModule  {
    private static final String[] ALIASES = new String[]{"IAmRoles"};
    private static final String DESCRIPTION = "Controller for IAmRoles.";
    private static final String[] ALIASES_I_AM_NOT = new String[]{"IAmNot"};
    private static final String DESCRIPTION_I_AM_NOT = "Can be used to remove a role from yourself.";
    private static final String[] ALIASES_I_AM = new String[]{"iam"};
    private static final String DESCRIPTION_I_AM = "Can be used to self assign a role.";

    public IAmRoles() {
        super(ALIASES, null, DESCRIPTION);
    }

    @Override
    public void commandExec(@NotNull MessageReceivedEvent event, @NotNull String command, String arguments) {
        event.getJDA().addEventListener(new IAmRolesSequence(event.getAuthor(), event.getChannel()));
    }

    public class IAmRolesSequence extends Sequence {


        IAmRolesSequence(@NotNull User user, @NotNull MessageChannel channel) {
            super(user, channel);
        }

        @Override
        public void onMessageReceivedDuringSequence(@NotNull MessageReceivedEvent event) {
            //todo write logic
        }
    }

    class IAmRolesCategory {
        private long guildId;
        private boolean canOnlyHaveOne;
        private ArrayList<Long> roles;

        public IAmRolesCategory(long guildId, boolean canOnlyHaveOne) {
            this.guildId = guildId;
            this.canOnlyHaveOne = canOnlyHaveOne;
            this.roles = new ArrayList<>();
        }

        public IAmRolesCategory(@JSONKey(jsonKey = "guildId") long guildId, @JSONKey(jsonKey = "canOnlyHaveOne") boolean canOnlyHaveOne, @JSONKey(jsonKey = "roles") JSONArray roles) {
            this.guildId = guildId;
            this.canOnlyHaveOne = canOnlyHaveOne;
            this.roles = new ArrayList<>(JSONToJavaObject.INSTANCE.toTypedList(roles, Long.class));
        }

        @JSONKey(jsonKey = "guildId")
        public long getGuildId() {
            return guildId;
        }

        @JSONKey(jsonKey = "roles")
        public List<Long> getRoles() {
            return Collections.unmodifiableList(roles);
        }

        @JSONKey(jsonKey = "canOnlyHaveOne")
        public boolean isCanOnlyHaveOne() {
            return canOnlyHaveOne;
        }
    }

    /**
     * I am not command to allow users to remove roles from them self.
     * <p>
     * Created by Duncan on 23/02/2017.
     */
    class IAmNot extends CommandModule {

        IAmNot() {
            super(ALIASES_I_AM_NOT, null, DESCRIPTION_I_AM_NOT);
        }

        @Override
        public void commandExec(MessageReceivedEvent event, String command, String arguments) {
            //todo rewrite
        }
    }

    /**
     * Iam command to assign yourself roles.
     * <p>
     * Created by Duncan on 23/02/2017.
     */
    class IAm extends CommandModule {

        IAm() {
            super(ALIASES_I_AM, null, DESCRIPTION_I_AM);
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
            //todo rewrite
        }
    }
}
