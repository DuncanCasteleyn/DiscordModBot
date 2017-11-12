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
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;

public class IAmRoles extends CommandModule {
    private static final String[] ALIASES = new String[]{"IAmRoles"};
    private static final String DESCRIPTION = "Controller for IAmRoles.";
    private static final String[] ALIASES_I_AM_NOT = new String[]{"IAmNot"};
    private static final String DESCRIPTION_I_AM_NOT = "Can be used to remove a role from yourself.";
    private static final String[] ALIASES_I_AM = new String[]{"iam"};
    private static final String DESCRIPTION_I_AM = "Can be used to self assign a role.";

    public IAmRoles() {
        super(ALIASES, null, DESCRIPTION);
        iAmRoles = new HashMap<>();
    }

    public IAmRoles(HashMap<String, JSONArray> iAmRoles) {
        super(ALIASES, null, DESCRIPTION);
        throw new UnsupportedOperationException("Not yet implemented");
        /*this.iAmRoles = new HashMap<>();
        iAmRoles.forEach((s, ) -> {
            //this.iAmRoles.put(Long.parseLong(s), );
            //todo
        });*/
    }

    private HashMap<Long, ArrayList<IAmRolesCategory>> iAmRoles;

    @Override
    public void commandExec(@NotNull MessageReceivedEvent event, @NotNull String command, String arguments) {
        event.getJDA().addEventListener(new IAmRolesSequence(event.getAuthor(), event.getChannel()));
    }

    public class IAmRolesSequence extends Sequence {

        private byte sequenceNumber;
        private String newCatogoryName = null;

        IAmRolesSequence(@NotNull User user, @NotNull MessageChannel channel) {
            super(user, channel);
            sequenceNumber = 0;
        }

        @Override
        public void onMessageReceivedDuringSequence(@NotNull MessageReceivedEvent event) {
            switch (sequenceNumber) {
                case 0:
                    super.getChannel().sendMessage("Please select which action you want to perform:\n" +
                            "0. Add a new category\n" +
                            "1. Remove a existing category\n" +
                            "2. Modify a existing category").queue(message -> super.addMessageToCleaner(message));
                    sequenceNumber = 1;
                    break;
                case 1:
                    switch (Byte.parseByte(event.getMessage().getRawContent())) {
                        case 0:
                            super.getChannel().sendMessage("Please enter a unique category name and if you can only have one role of this category (true if the a user can only have on role out of this category). Syntax: \"Role name | true or false\"").queue(message -> super.addMessageToCleaner(message));
                            sequenceNumber = 2;
                            break;
                        case 1:
                            //todo logic to remove existing categories
                            sequenceNumber = 3;
                            break;
                        case 2:
                            //todo logic to add roles to existing categories
                            sequenceNumber = 4;
                            break;
                        default:
                            getChannel().sendMessage("Wrong answer please answer with a valid number").queue(message -> super.addMessageToCleaner(message));
                            break;
                    }
                    break;
                case 2:
                    if(newCatogoryName == null) {
                        ArrayList<String> existingCategoryNames = new ArrayList<>();
                        iAmRoles.get(event.getGuild().getIdLong()).forEach(iAmRolesCategory -> existingCategoryNames.add(iAmRolesCategory.categoryName));
                        if (existingCategoryNames.contains(event.getMessage().getRawContent())) {
                            throw new IllegalArgumentException("The name you provided is already being used.");
                        }
                        newCatogoryName = event.getMessage().getRawContent();
                        super.getChannel().sendMessage("Please enter if a user can only have on role of this category. true or false?").queue(message -> super.addMessageToCleaner(message));
                    } else {
                        //todo
                    }
                    break;
                case 3:
                    //todo
                    break;
                case 4:
                    //todo
                    break;
            }
        }
    }

    class IAmRolesCategory {
        private String categoryName;
        private boolean canOnlyHaveOne;
        private ArrayList<Long> roles;

        public IAmRolesCategory(String categoryName, boolean canOnlyHaveOne) {
            this.categoryName = categoryName;
            this.canOnlyHaveOne = canOnlyHaveOne;
            this.roles = new ArrayList<>();
        }

        public IAmRolesCategory(@JSONKey(jsonKey = "categoryName") String categoryName, @JSONKey(jsonKey = "canOnlyHaveOne") boolean canOnlyHaveOne, @JSONKey(jsonKey = "roles") JSONArray roles) {
            this.categoryName = categoryName;
            this.canOnlyHaveOne = canOnlyHaveOne;
            this.roles = new ArrayList<>(JSONToJavaObject.INSTANCE.toTypedList(roles, Long.class));
        }

        @JSONKey(jsonKey = "guildId")
        public String getCategoryName() {
            return categoryName;
        }

        @JSONKey(jsonKey = "roles")
        public List<Long> getRoles() {
            return Collections.unmodifiableList(roles);
        }

        @JSONKey(jsonKey = "canOnlyHaveOne")
        public boolean canOnlyHaveOne() {
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
        public void commandExec(@NotNull MessageReceivedEvent event, @NotNull String command, String arguments) {
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
        public void commandExec(@NotNull MessageReceivedEvent event, @NotNull String command, String arguments) {
            //todo rewrite
        }
    }
}
